package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.javaoperatorsdk.operator.processing.ProcessingUtils;
import io.javaoperatorsdk.operator.processing.ResourceCache;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.javaoperatorsdk.operator.processing.ProcessingUtils.*;
import static java.net.HttpURLConnection.HTTP_GONE;

/**
 * This is a special case since is not bound to a single custom resource
 */
public class CustomResourceEventSource extends AbstractEventSource implements Watcher<CustomResource> {

    private final static Logger log = LoggerFactory.getLogger(CustomResourceEventSource.class);

    private final ResourceCache resourceCache;
    private MixedOperation client;
    private final String[] targetNamespaces;

    public static CustomResourceEventSource customResourceEventSourceForAllNamespaces(ResourceCache resourceCache,
                                                                                      MixedOperation client) {
        return new CustomResourceEventSource(resourceCache, client, null);
    }

    public static CustomResourceEventSource customResourceEventSourceForTargetNamespaces(ResourceCache resourceCache,
                                                                                         MixedOperation client,
                                                                                         String[] namespaces) {
        return new CustomResourceEventSource(resourceCache, client, namespaces);
    }

    private CustomResourceEventSource(ResourceCache resourceCache, MixedOperation client, String[] targetNamespaces) {
        this.resourceCache = resourceCache;
        this.client = client;
        this.targetNamespaces = targetNamespaces;
    }

    private boolean isWatchAllNamespaces() {
        return targetNamespaces == null;
    }

    public void addedToEventManager() {
        registerWatch();
    }

    private void registerWatch() {
        CustomResourceOperationsImpl crClient = (CustomResourceOperationsImpl) client;
        if (isWatchAllNamespaces()) {
            crClient.inAnyNamespace().watch(this);
        } else if (targetNamespaces.length == 0) {
            client.watch(this);
        } else {
            for (String targetNamespace : targetNamespaces) {
                crClient.inNamespace(targetNamespace).watch(this);
                log.debug("Registered controller for namespace: {}", targetNamespace);
            }
        }
    }

    @Override
    public void eventReceived(Watcher.Action action, CustomResource customResource) {
        log.debug("Event received for action: {}, resource: {}",
                customResource.getMetadata().getName(), customResource);
        resourceCache.cacheResource(customResource); // always store the latest event. Outside the sync block is intentional.
        if (action == Action.ERROR) {
            log.debug("Skipping {} event for custom resource uid: {}, version: {}", action,
                    getUID(customResource), getVersion(customResource));
            return;
        }
        eventHandler.handleEvent(new CustomResourceEvent(action, customResource, this));
    }

    @Override
    public void onClose(KubernetesClientException e) {
        if (e == null) {
            return;
        }
        if (e.getCode() == HTTP_GONE) {
            log.warn("Received error for watch, will try to reconnect.", e);
            registerWatch();
        } else {
            // Note that this should not happen normally, since fabric8 client handles reconnect.
            // In case it tries to reconnect this method is not called.
            log.error("Unexpected error happened with watch. Will exit.", e);
            System.exit(1);
        }
    }
}
