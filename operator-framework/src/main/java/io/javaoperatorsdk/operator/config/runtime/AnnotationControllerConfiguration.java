package io.javaoperatorsdk.operator.config.runtime;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public class AnnotationControllerConfiguration<R extends HasMetadata>
    extends io.javaoperatorsdk.operator.api.config.AnnotationControllerConfiguration<R> {

  public AnnotationControllerConfiguration(Reconciler<R> reconciler) {
    super(reconciler);
  }

  @Override
  public Class<R> getResourceClass() {
    return RuntimeControllerMetadata.getResourceClass(reconciler);
  }
}
