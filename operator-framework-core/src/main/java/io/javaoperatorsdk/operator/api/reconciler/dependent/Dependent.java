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
   * <p>
   * If the condition is not met, the dependent resource won't be used. That is, no event sources
   * will be registered for the dependent, and won't be reconciled or deleted. If other dependents
   * are still "depend on" this resource, those still will be deleted when needed. Exactly the same
   * way as for the reconcilePrecondition.
   * </p>
   * <p>
   * This condition is evaluated dynamically, thus on every reconciliation as other conditions. Thus
   * it's result can change dynamically, therefore the event source that the dependent resource
   * provides are registered or de-registered dynamically during the reconciliation.
   * </p>
   */
  Class<? extends Condition> activationCondition() default Condition.class;

  /**
   * The list of named dependents that need to be reconciled before this one can be.
   *
   * @return the list (possibly empty) of named dependents that need to be reconciled before this
   *         one can be
   */
  String[] dependsOn() default {};

  /**
   * Setting here a name of the event source means that dependent resource will use an event source
   * registered with that name. So won't create one. This is helpful if more dependent resources
   * created for the same type, and want to share a common event source.
   *
   * @return event source name (if any) provided by the dependent resource should be used.
   */
  String useEventSourceWithName() default NO_VALUE_SET;
}
