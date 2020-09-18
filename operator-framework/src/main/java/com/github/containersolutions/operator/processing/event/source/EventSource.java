package com.github.containersolutions.operator.processing.event.source;

import com.github.containersolutions.operator.processing.event.EventHandler;
import com.github.containersolutions.operator.processing.event.ExecutionDescriptor;
import io.fabric8.kubernetes.client.CustomResource;

public interface EventSource extends GenericEventSource {

    void eventSourceRegisteredForResource(CustomResource customResource);

    void eventSourceDeRegisteredForResource(String customResourceUid);

}
