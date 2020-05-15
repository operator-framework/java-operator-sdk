package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class DoneableMinioInstance extends CustomResourceDoneable<MinioInstance> {
    public DoneableMinioInstance(MinioInstance resource, Function function) {
        super(resource, function);
    }
}
