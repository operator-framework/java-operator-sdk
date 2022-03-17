package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;

/**
 * Adaptor Class for standalone mode for resources that manages Create, Update and Delete
 *
 * @param <R> Managed resource
 * @param <P> Primary Resource
 */
public abstract class CruKubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends
    KubernetesDependentResource<R, P> implements Creator<R, P>, Updater<R, P> {
}
