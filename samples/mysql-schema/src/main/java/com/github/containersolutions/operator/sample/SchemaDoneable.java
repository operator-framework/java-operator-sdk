package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class SchemaDoneable extends CustomResourceDoneable<Schema> {
    public SchemaDoneable(Schema resource, Function<Schema, Schema> function) {
        super(resource, function);
    }
}
