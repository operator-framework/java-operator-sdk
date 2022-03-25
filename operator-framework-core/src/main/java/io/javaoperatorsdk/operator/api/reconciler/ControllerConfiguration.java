package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ControllerConfiguration {

  String name() default Constants.EMPTY_STRING;

  /**
   * Optional finalizer name, if it is not provided, one will be automatically generated. Note that
   * finalizers are only added when Reconciler implement {@link Cleaner} interface, or at least one
   * managed dependent resource implement
   * {@link io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter} interface.
   *
   * @return the finalizer name
   */
  String finalizerName() default Constants.EMPTY_STRING;

  /**
   * If true, will dispatch new event to the controller if generation increased since the last
   * processing, otherwise will process all events. See generation meta attribute <a href=
   * "https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definitions/#status-subresource">here</a>
   *
   * @return whether the controller takes generation into account to process events
   */
  boolean generationAwareEventProcessing() default true;

  /**
   * Specified which namespaces this Controller monitors for custom resources events. If no
   * namespace is specified then the controller will monitor all namespaces by default.
   *
   * @return the list of namespaces this controller monitors
   */
  String[] namespaces() default {};

  /**
   * Optional label selector used to identify the set of custom resources the controller will acc
   * upon. The label selector can be made of multiple comma separated requirements that acts as a
   * logical AND operator.
   *
   * @return the label selector
   */
  String labelSelector() default Constants.EMPTY_STRING;

  /**
   * <p>
   * Resource event filters only applies on events of the main custom resource. Not on events from
   * other event sources nor the periodic events.
   * </p>
   *
   * @return the list of event filters.
   */
  Class<? extends ResourceEventFilter>[] eventFilters() default {};

  /**
   * Optional configuration of the maximal interval the SDK will wait for a reconciliation request
   * to happen before one will be automatically triggered.
   *
   * @return the maximal interval configuration
   */
  ReconciliationMaxInterval reconciliationMaxInterval() default @ReconciliationMaxInterval(
      interval = 10);

  /**
   * Optional list of {@link Dependent} configurations which associate a resource type to a
   * {@link io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource} implementation
   *
   * @return the list of {@link Dependent} configurations
   */
  Dependent[] dependents() default {};

  boolean dependentErrorFailsReconciliation() default Constants.DEPENDENT_ERROR_FAILS_RECONCILIATION_DEFAULT;
}
