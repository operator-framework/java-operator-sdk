package io;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import java.lang.SuppressWarnings;

public class MyCustomResourceDoneable extends CustomResourceDoneable<MultilevelController.MyCustomResource> {
    @SuppressWarnings("unchecked")
    public MyCustomResourceDoneable(MultilevelController.MyCustomResource resource, Function function) {
        super(resource, function);
    }
}
