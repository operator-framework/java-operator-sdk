package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.javaoperatorsdk.operator.api.config.ControllerMode;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.config.ControllerConfiguration.CONTROLLER_NAME_AS_FIELD_MANAGER;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ControllerConfiguration {

  String name() default Constants.NO_VALUE_SET;

  Informer informer() default @Informer;

  /**
   * Optional finalizer name, if it is not provided, one will be automatically generated. Note that
   * finalizers are only added when Reconciler implement {@link Cleaner} interface and/or at least
   * one managed dependent resource implements the {@link
   * io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter} interface.
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
   * Optional configuration of the maximal interval the SDK will wait for a reconciliation request
   * to happen before one will be automatically triggered. The intention behind this feature is to
   * have a failsafe, not to artificially force repeated reconciliations. For that use {@link
   * UpdateControl#rescheduleAfter(long)}.
   *
   * @return the maximal reconciliation interval configuration
   */
  MaxReconciliationInterval maxReconciliationInterval() default
      @MaxReconciliationInterval(interval = MaxReconciliationInterval.DEFAULT_INTERVAL);

  /**
   * Optional {@link Retry} implementation for the associated controller to use.
   *
   * @return the class providing the {@link Retry} implementation to use, needs to provide an
   *     accessible no-arg constructor.
   */
  Class<? extends Retry> retry() default GenericRetry.class;

  /**
   * Optional {@link RateLimiter} implementation for the associated controller to use.
   *
   * @return the class providing the {@link RateLimiter} implementation to use, needs to provide an
   *     accessible no-arg constructor.
   */
  Class<? extends RateLimiter> rateLimiter() default LinearRateLimiter.class;

  /**
   * Retrieves the name used to assign as field manager for <a
   * href="https://kubernetes.io/docs/reference/using-api/server-side-apply/">Server-Side Apply</a>
   * (SSA) operations. If unset, the sanitized controller name will be used.
   *
   * @return the name used as field manager for SSA operations
   */
  String fieldManager() default CONTROLLER_NAME_AS_FIELD_MANAGER;

  ControllerMode controllerMode() default ControllerMode.DEFAULT;
}
