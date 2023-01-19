package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ControllerConfiguration {

  String name() default Constants.NO_VALUE_SET;

  /**
   * Optional finalizer name, if it is not provided, one will be automatically generated. Note that
   * finalizers are only added when Reconciler implement {@link Cleaner} interface and/or at least
   * one managed dependent resource implements the
   * {@link io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter} interface.
   *
   * @return the finalizer name
   */
  String finalizerName() default Constants.NO_VALUE_SET;

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
  String[] namespaces() default Constants.WATCH_ALL_NAMESPACES;

  /**
   * Optional label selector used to identify the set of custom resources the controller will acc
   * upon. The label selector can be made of multiple comma separated requirements that acts as a
   * logical AND operator.
   *
   * @return the label selector
   */
  String labelSelector() default Constants.NO_VALUE_SET;

  /**
   * @deprecated Use onAddFilter, onUpdateFilter instead.
   *
   *             <p>
   *             Resource event filters only applies on events of the main custom resource. Not on
   *             events from other event sources nor the periodic events.
   *             </p>
   *
   * @return the list of event filters.
   */
  @Deprecated(forRemoval = true)
  Class<? extends ResourceEventFilter>[] eventFilters() default {};

  /**
   * Filter of onAdd events of resources.
   **/
  Class<? extends OnAddFilter> onAddFilter() default OnAddFilter.class;

  /** Filter of onUpdate events of resources. */
  Class<? extends OnUpdateFilter> onUpdateFilter() default OnUpdateFilter.class;

  /**
   * Filter applied to all operations (add, update, delete). Used to ignore some resources.
   **/
  Class<? extends GenericFilter> genericFilter() default GenericFilter.class;

  /**
   * Optional configuration of the maximal interval the SDK will wait for a reconciliation request
   * to happen before one will be automatically triggered.
   *
   * @return the maximal reconciliation interval configuration
   */
  MaxReconciliationInterval maxReconciliationInterval() default @MaxReconciliationInterval(
      interval = MaxReconciliationInterval.DEFAULT_INTERVAL);


  /**
   * Optional list of {@link Dependent} configurations which associate a resource type to a
   * {@link io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource} implementation
   *
   * @return the list of {@link Dependent} configurations
   */
  Dependent[] dependents() default {};

  /**
   * Optional {@link Retry} implementation for the associated controller to use.
   *
   * @return the class providing the {@link Retry} implementation to use, needs to provide an
   *         accessible no-arg constructor.
   */
  Class<? extends Retry> retry() default GenericRetry.class;

  /**
   * Optional {@link RateLimiter} implementation for the associated controller to use.
   *
   * @return the class providing the {@link RateLimiter} implementation to use, needs to provide an
   *         accessible no-arg constructor.
   */
  Class<? extends RateLimiter> rateLimiter() default LinearRateLimiter.class;

  Class<? extends ItemStore> itemStore() default ItemStore.class;
}
