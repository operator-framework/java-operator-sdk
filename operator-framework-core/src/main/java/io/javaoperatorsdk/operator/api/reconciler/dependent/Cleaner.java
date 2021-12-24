package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface Cleaner<R, P extends HasMetadata> {

  void delete(R fetched, P primary, Context context);
}
