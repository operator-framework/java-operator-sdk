package io;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class MyCustomResourceDoneable extends CustomResourceDoneable<ControllerImplemented2Interfaces.MyCustomResource> {
    public MyCustomResourceDoneable(ControllerImplemented2Interfaces.MyCustomResource resource, Function function) {
        super(resource, function);
    }
}
