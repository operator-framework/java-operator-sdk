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
import io.javaoperatorsdk.operator.processing.event.source.cache.BoundedItemStore;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.config.ControllerConfiguration.CONTROLLER_NAME_AS_FIELD_MANAGER;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_LONG_VALUE_SET;

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
   * @return the array of namespaces this controller monitors
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
   * Filter of onAdd events of resources.
   *
   * @return on-add filter
   **/
  Class<? extends OnAddFilter> onAddFilter() default OnAddFilter.class;

  /**
   * Filter of onUpdate events of resources.
   *
   * @return on-update filter
   */
  Class<? extends OnUpdateFilter> onUpdateFilter() default OnUpdateFilter.class;

  /**
   * Filter applied to all operations (add, update, delete). Used to ignore some resources.
   *
   * @return generic filter
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
   * @return the array of {@link Dependent} configurations
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

  /**
   * Replaces the item store used by the informer for the associated primary resource controller.
   * See underlying <a href=
   * "https://github.com/fabric8io/kubernetes-client/blob/43b67939fde91046ab7fb0c362f500c2b46eb59e/kubernetes-client/src/main/java/io/fabric8/kubernetes/client/informers/impl/DefaultSharedIndexInformer.java#L273">
   * method</a> in fabric8 client informer implementation.
   *
   * <p>
   * The main goal, is to be able to use limited caches or provide any custom implementation.
   * </p>
   *
   * <p>
   * See {@link BoundedItemStore} and <a href=
   * "https://github.com/operator-framework/java-operator-sdk/blob/main/caffeine-bounded-cache-support/src/main/java/io/javaoperatorsdk/operator/processing/event/source/cache/CaffeineBoundedCache.java">CaffeinBoundedCache</a>
   * </p>
   *
   * @return the class of the {@link ItemStore} implementation to use
   */
  Class<? extends ItemStore> itemStore() default ItemStore.class;

  /**
   * Retrieves the name used to assign as field manager for
   * <a href="https://kubernetes.io/docs/reference/using-api/server-side-apply/">Server-Side
   * Apply</a> (SSA) operations. If unset, the sanitized controller name will be used.
   *
   * @return the name used as field manager for SSA operations
   */
  String fieldManager() default CONTROLLER_NAME_AS_FIELD_MANAGER;

  /**
   * The maximum amount of items to return for a single list call when starting the primary resource
   * related informers. If this is a not null it will result in paginating for the initial load of
   * the informer cache.
   */
  long informerListLimit() default NO_LONG_VALUE_SET;
}
