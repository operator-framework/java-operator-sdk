package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;
import java.io.Serializable;


public interface AdditionalControllerInterface<R, T extends CustomResource> extends
    Serializable,
    ResourceController<T> {
}
