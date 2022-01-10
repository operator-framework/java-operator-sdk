package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

public class CustomFilter implements ResourceEventFilter {
    @Override
    public boolean acceptChange(ControllerConfiguration configuration, HasMetadata oldResource, HasMetadata newResource) {
        return false;
    }
}
