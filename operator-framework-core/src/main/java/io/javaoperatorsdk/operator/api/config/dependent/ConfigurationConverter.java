package io.javaoperatorsdk.operator.api.config.dependent;

import java.lang.annotation.Annotation;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ConfiguredDependentResource;

public interface ConfigurationConverter<A extends Annotation, C, D extends ConfiguredDependentResource<? extends C>> {

  C configFrom(A configAnnotation, ControllerConfiguration<?> parentConfiguration,
      Class<D> originatingClass);
}
