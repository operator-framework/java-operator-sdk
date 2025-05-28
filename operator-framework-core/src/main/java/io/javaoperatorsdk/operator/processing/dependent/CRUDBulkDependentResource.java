package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;

public interface CRUDBulkDependentResource<R, P extends HasMetadata>
    extends BulkDependentResource<R, P>, Creator<R, P>, BulkUpdater<R, P>, Deleter<P> {}
