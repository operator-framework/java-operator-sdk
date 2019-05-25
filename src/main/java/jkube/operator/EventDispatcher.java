package jkube.operator;

import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDispatcher<R extends CustomResource,
        L extends CustomResourceList<R>,
        D extends CustomResourceDoneable<R>> implements Watcher<R> {

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final CustomResourceController<R, L, D> controller;
    private final NonNamespaceOperation<R, L, D, Resource<R, D>> resourceClient;

    public EventDispatcher(CustomResourceController<R, L, D> controller,
                           NonNamespaceOperation<R, L, D, Resource<R, D>> resourceClient) {
        this.controller = controller;
        this.resourceClient = resourceClient;
    }

    public void eventReceived(Action action, R resource) {
        log.info("Action: {}, Event: {}", action, resource);

        if (action == Action.MODIFIED || action == Action.ADDED) {
            if (markedForDeletion(resource)) {
                controller.deleteResource(resource);
            } else {
                R updatedResource = controller.createOrUpdateResource(resource);
                replace(updatedResource);
            }
        }

        if (Action.ERROR == action) {
            log.error("Received error for resource: {}", resource.getMetadata().getName());
        }
    }

    private void replace(R resource) {
        var resourceOperation = (CustomResourceOperationsImpl<R, L, D>) resourceClient;
        resourceOperation.lockResourceVersion(resource.getMetadata().getResourceVersion()).replace(resource);
    }

    private boolean markedForDeletion(R resource) {
        return resource.getMetadata().getDeletionTimestamp() != null && !resource.getMetadata().getDeletionTimestamp().isEmpty();
    }

    @Override
    public void onClose(KubernetesClientException e) {
        if (e != null) {
            log.error("Error: ", e);
        }
    }
}
