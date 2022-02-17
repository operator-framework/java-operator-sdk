package io.javaoperatorsdk.operator.api.reconciler.dependent;

public interface DependentResourceConfigurator<C> {
  void configureWith(C config);
}
