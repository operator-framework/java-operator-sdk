package io;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class MyCustomResourceDoneable extends CustomResourceDoneable<MultilevelController.MyCustomResource> {
    public MyCustomResourceDoneable(MultilevelController.MyCustomResource resource, Function function) {
        super(resource, function);
    }
}
