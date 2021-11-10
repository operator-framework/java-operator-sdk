package io;

import io.fabric8.kubernetes.client.CustomResource;
import java.io.Serializable;


public abstract class MultilevelAbstractReconciler<R, T extends CustomResource<?,?>> implements
    Serializable,
    AdditionalReconcilerInterface<R, T> {

}
