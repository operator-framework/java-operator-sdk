package com.github.containersolutions.operator.spingboot.starter.model;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class TestResourceDoneable extends CustomResourceDoneable<TestResource> {
    public TestResourceDoneable(TestResource resource, Function<TestResource, TestResource> function) {
        super(resource, function);
    }
}
