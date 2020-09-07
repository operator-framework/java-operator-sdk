package com.github.containersolutions.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

public interface Event {

    String getRelatedCustomResourceUid();

}
