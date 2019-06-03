package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class TestCustomResourceDoneable extends CustomResourceDoneable<TestCustomResource> {

    public TestCustomResourceDoneable(TestCustomResource resource, Function<TestCustomResource, TestCustomResource> function) {
        super(resource, function);
    }
}
