package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.event.EventHandler;
import com.github.containersolutions.operator.processing.event.EventSource;
import com.github.containersolutions.operator.processing.event.ExecutionDescriptor;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;

public class CustomResourceEventSource implements EventSource, Watcher<CustomResource> {

    @Override
    public void setEventHandler(EventHandler eventHandler) {

    }

    @Override
    public void eventSourceRegistered(String customResourceUid) {

    }

    @Override
    public void eventSourceDeRegistered(String customResourceUid) {

    }

    @Override
    public void eventProcessingFinished(ExecutionDescriptor executionDescriptor) {

    }

    @Override
    public void eventReceived(Action action, CustomResource customResource) {

    }

    @Override
    public void onClose(KubernetesClientException e) {

    }
}
