package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface MaxReconciliationInterval {

  long DEFAULT_INTERVAL = 10;

  /**
   * A max delay between two reconciliations. Having this value larger than zero, will ensure that a
   * reconciliation is scheduled with a target interval after the last reconciliation. Note that
   * this not applies for retries, in case of an exception reconciliation is not scheduled. This is
   * not a fixed rate, in other words a new reconciliation is scheduled after each reconciliation.
   *
   * <p>If an interval is specified by {@link UpdateControl} or {@link DeleteControl}, those take
   * precedence.
   *
   * <p>This is a fail-safe feature, in the sense that if informers are in place and the reconciler
   * implementation is correct, this feature can be turned off.
   *
   * <p>Use {@link Constants#NO_MAX_RECONCILIATION_INTERVAL} to turn off this feature.
   *
   * @return max delay between reconciliations
   */
  long interval();

  /**
   * @return time unit for max delay between reconciliations
   */
  TimeUnit timeUnit() default TimeUnit.HOURS;
}
