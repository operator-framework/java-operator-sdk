package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

/**
 * The annotation used to create managed {@link DependentResource} associated with a given
 * {@link io.javaoperatorsdk.operator.api.reconciler.Reconciler}
 */
public @interface Dependent {

  @SuppressWarnings("rawtypes")
  Class<? extends DependentResource> type();

  /**
   * The name of this dependent. This is needed to be able to refer to it when creating dependencies
   * between dependent resources.
   *
   * @return the name if it has been set,
   *         {@link io.javaoperatorsdk.operator.api.reconciler.Constants#NO_VALUE_SET} otherwise
   */
  String name() default NO_VALUE_SET;

  /**
   * The condition (if it exists) that needs to become true before the workflow can further proceed.
   *
   * @return a {@link Condition} implementation, defaulting to the interface itself if no value is
   *         set
   */
  Class<? extends Condition> readyPostcondition() default Condition.class;

  /**
   * The condition (if it exists) that needs to become true before the associated
   * {@link DependentResource} is reconciled. Note that if this condition is set and the condition
   * doesn't hold true, the associated secondary will be deleted.
   *
   * @return a {@link Condition} implementation, defaulting to the interface itself if no value is
   *         set
   */
  Class<? extends Condition> reconcilePrecondition() default Condition.class;

  /**
   * The condition (if it exists) that needs to become true before further reconciliation of
   * dependents can proceed after the secondary resource associated with this dependent is supposed
   * to have been deleted.
   *
   * @return a {@link Condition} implementation, defaulting to the interface itself if no value is
   *         set
   */
  Class<? extends Condition> deletePostcondition() default Condition.class;

  /**
   * The list of named dependents that need to be reconciled before this one can be.
   *
   * @return the list (possibly empty) of named dependents that need to be reconciled before this
   *         one can be
   */
  String[] dependsOn() default {};
}
