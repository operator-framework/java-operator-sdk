package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import java.io.Serializable;


public interface AdditionalReconcilerInterface<R, T extends CustomResource<?,?>> extends
    Serializable,
        Reconciler<T> {
}
