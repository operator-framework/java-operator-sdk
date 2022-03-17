package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Cleaner;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;

/**
 * Adaptor Class for standalone mode for resources that manages Create, Read, Update and Delete
 *
 * @param <R> Managed resource
 * @param <P> Primary Resource
 */
public abstract class CRUDKubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends
    KubernetesDependentResource<R, P> implements Creator<R, P>, Updater<R, P>, Cleaner<P> {
}
