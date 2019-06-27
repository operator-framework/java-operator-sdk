package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class NginxWwwDoneable extends CustomResourceDoneable<NginxWww> {
    public NginxWwwDoneable(NginxWww resource, Function<NginxWww, NginxWww> function) {
        super(resource, function);
    }
}
