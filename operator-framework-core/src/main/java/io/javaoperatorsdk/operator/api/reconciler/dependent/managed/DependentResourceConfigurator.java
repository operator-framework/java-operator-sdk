package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

public interface DependentResourceConfigurator<C> {
  void configureWith(C config);
}
