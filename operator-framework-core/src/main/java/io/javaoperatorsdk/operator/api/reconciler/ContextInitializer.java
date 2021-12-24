package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface ContextInitializer<P extends HasMetadata> {
  void initContext(P primary, Context context);
}
