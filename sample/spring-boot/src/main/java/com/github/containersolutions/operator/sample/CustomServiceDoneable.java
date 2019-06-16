package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class CustomServiceDoneable extends CustomResourceDoneable<CustomService> {
    public CustomServiceDoneable(CustomService resource, Function<CustomService, CustomService> function) {
        super(resource, function);
    }
}
