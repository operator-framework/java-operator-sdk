package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.lang.annotation.Annotation;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

public interface AnnotationDependentResourceConfigurator<A extends Annotation, C>
    extends DependentResourceConfigurator<C> {

  C configFrom(A annotation, ControllerConfiguration<?> parentConfiguration);
}
