package io.javaoperatorsdk.operator.api.config.dependent;

import java.lang.annotation.Annotation;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;

public interface ConfigurationConverter<A extends Annotation, C, D extends DependentResourceConfigurator<? extends C>> {

  C configFrom(A configAnnotation, ControllerConfiguration<?> parentConfiguration,
      Class<D> originatingClass);
}
