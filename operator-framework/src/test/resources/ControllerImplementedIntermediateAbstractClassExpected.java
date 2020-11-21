package io;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class MyCustomResourceDoneable extends CustomResourceDoneable<AbstractController.MyCustomResource> {
    public MyCustomResourceDoneable(AbstractController.MyCustomResource resource, Function function) {
        super(resource, function);
    }
}
