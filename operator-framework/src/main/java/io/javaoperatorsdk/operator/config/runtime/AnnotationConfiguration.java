package io.javaoperatorsdk.operator.config.runtime;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;

/** @deprecated use {@link io.javaoperatorsdk.operator.api.config.AnnotationConfiguration} */
@Deprecated
@SuppressWarnings("unchecked")
public class AnnotationConfiguration<R extends CustomResource>
    extends io.javaoperatorsdk.operator.api.config.AnnotationConfiguration<R> {

  public AnnotationConfiguration(ResourceController<R> controller) {
    super(
        (Class<ResourceController<R>>) controller.getClass(),
        RuntimeControllerMetadata.getCustomResourceClass(controller));
  }
}
