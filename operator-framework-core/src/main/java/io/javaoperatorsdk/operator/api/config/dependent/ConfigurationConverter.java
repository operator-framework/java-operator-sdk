package io.javaoperatorsdk.operator.api.config.dependent;

import java.lang.annotation.Annotation;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

public interface ConfigurationConverter<A extends Annotation, C> {

  C configFrom(
      A configAnnotation,
      DependentResourceSpec<?, ?, C> spec,
      ControllerConfiguration<?> parentConfiguration);
}
