package com.github.containersolutions.operator.processing.event.internal;

import com.github.containersolutions.operator.processing.ResourceCache;
import com.github.containersolutions.operator.processing.event.AbstractEventSource;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * This is a special case since does not bounds to a single custom resource
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
    public void eventReceived(Watcher.Action action, CustomResource resource) {
        log.debug("Event received for action: {}, {}: {}", action.toString().toLowerCase(), resource.getClass().getSimpleName(),
                resource.getMetadata().getName());
        resourceCache.cacheResource(resource); // always store the latest event. Outside the sync block is intentional.
        if (action == Action.ERROR) {
            log.debug("Skipping {} event for custom resource: {}", action, resource);
            return;
        }
        eventHandler.handleEvent(new CustomResourceEvent(action, resource, this));
    }

    @Override
    public void onClose(KubernetesClientException e) {
        // todo handle the fabric8 issue
        log.error("Error: ", e);
        // we will exit the application if there was a watching exception, because of the bug in fabric8 client
        // see https://github.com/fabric8io/kubernetes-client/issues/1318
        // Note that this should not happen normally, since fabric8 client handles reconnect.
        // In case it tries to reconnect this method is not called.
        System.exit(1);
    }
}
