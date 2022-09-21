package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

public interface InformerStoppedHandler {

  @SuppressWarnings("rawtypes")
  void onStop(SharedIndexInformer informer, Throwable ex);
}
