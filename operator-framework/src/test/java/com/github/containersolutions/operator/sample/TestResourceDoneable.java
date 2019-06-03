package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class TestResourceDoneable extends CustomResourceDoneable<TestCustomResource> {

    public TestResourceDoneable(TestCustomResource resource, Function<TestCustomResource, TestCustomResource> function) {
        super(resource, function);
    }
}
