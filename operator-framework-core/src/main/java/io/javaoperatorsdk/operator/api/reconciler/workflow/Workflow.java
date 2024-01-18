package io.javaoperatorsdk.operator.api.reconciler.workflow;

import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

public @interface Workflow {

  /**
   * List of {@link Dependent} configurations which associate a resource type to a
   * {@link io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource} implementation
   *
   * @return the array of {@link Dependent} configurations
   */
  Dependent[] dependents();

}
