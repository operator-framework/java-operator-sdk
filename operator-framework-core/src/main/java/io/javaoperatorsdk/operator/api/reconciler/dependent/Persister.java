package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface Persister<R, P extends HasMetadata> {

  void createOrReplace(R dependentResource, Context context);

  R getFor(P primary, Context context);
}
