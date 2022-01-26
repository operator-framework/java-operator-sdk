package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ControllerConfiguration {

  String name() default Constants.EMPTY_STRING;

  /**
   * Optional finalizer name, if it is not provided, one will be automatically generated. If the
   * provided value is the value specified by {@link Constants#NO_FINALIZER}, then no finalizer will
   * be added to custom resources.
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
   * Optional list of classes providing custom {@link ResourceEventFilter}.
   *
   * @return the list of event filters.
   */
  @SuppressWarnings("rawtypes")
  Class<? extends ResourceEventFilter>[] eventFilters() default {};

  /**
   * A max delay between two reconciliations. Having this value larger than zero, will ensure that a
   * reconciliation is scheduled with a target interval after the last reconciliation. Note that
   * this not applies for retries, in case of an exception reconciliation is not scheduled. This is
   * not a fixed rate, in other words a new reconciliation is scheduled after each reconciliation.
   *
   * If an interval is specified by {@link UpdateControl} or {@link DeleteControl}, those take
   * precedence.
   *
   * @return max delay between reconciliations
   **/
  long reconciliationMaxInterval() default 10;

  /**
   * @return time unit for max delay between reconciliations
   */
  TimeUnit reconciliationMaxIntervalTimeUnit() default TimeUnit.HOURS;
}
