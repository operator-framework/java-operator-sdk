package com.github.containersolutions.operator.processing.event.source;

import com.github.containersolutions.operator.processing.ResourceCache;
import com.github.containersolutions.operator.processing.event.EventHandler;
import com.github.containersolutions.operator.processing.event.ExecutionDescriptor;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomResourceEventSource implements Watcher<CustomResource> {

    private final static Logger log = LoggerFactory.getLogger(CustomResourceEventSource.class);

    private final EventHandler eventHandler;
    private final ResourceCache resourceCache;

    public CustomResourceEventSource(EventHandler eventHandler, ResourceCache resourceCache) {
        this.eventHandler = eventHandler;
        this.resourceCache = resourceCache;
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
