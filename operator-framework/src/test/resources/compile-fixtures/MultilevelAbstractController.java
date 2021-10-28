package io;

import io.fabric8.kubernetes.client.CustomResource;
import java.io.Serializable;


public abstract class MultilevelAbstractController<R, T extends CustomResource<?,?>> implements
    Serializable,
    AdditionalControllerInterface<R, T> {

}
