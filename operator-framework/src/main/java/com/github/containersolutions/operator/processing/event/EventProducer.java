package com.github.containersolutions.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

public interface EventProducer {

    void setEventHandler(EventHandler eventHandler);

    void eventProducerRegistered(String customResourceUid);

    void eventProducerDeRegistered(String customResourceUid);

}
