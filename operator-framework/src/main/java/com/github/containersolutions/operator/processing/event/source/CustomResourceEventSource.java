package com.github.containersolutions.operator.processing.event.source;

import com.github.containersolutions.operator.processing.ResourceCache;
import com.github.containersolutions.operator.processing.event.EventHandler;
import com.github.containersolutions.operator.processing.event.ExecutionDescriptor;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomResourceEventSource extends AbstractEventSource implements Watcher<CustomResource> {

    private final static Logger log = LoggerFactory.getLogger(CustomResourceEventSource.class);

    private final ResourceCache resourceCache;

    public CustomResourceEventSource(ResourceCache resourceCache) {
        this.resourceCache = resourceCache;
    }

    @Override
    public void setEventHandler(EventHandler eventHandler) {

    }

    @Override
    public void eventSourceRegisteredForResource(CustomResource customResourceUid) {

    }

    @Override
    public void eventSourceDeRegisteredForResource(String customResourceUid) {

    }

    @Override
    public void eventProcessingFinished(ExecutionDescriptor executionDescriptor) {

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
        log.error("Error: ", e);
        // we will exit the application if there was a watching exception, because of the bug in fabric8 client
        // see https://github.com/fabric8io/kubernetes-client/issues/1318
        // Note that this should not happen normally, since fabric8 client handles reconnect.
        // In case it tries to reconnect this method is not called.
        System.exit(1);
    }
}
