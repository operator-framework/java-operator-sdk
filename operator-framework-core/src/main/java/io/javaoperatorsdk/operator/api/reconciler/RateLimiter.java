package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import io.javaoperatorsdk.operator.processing.event.rate.PeriodRateLimiter;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface RateLimiter {

  int limitForPeriod() default PeriodRateLimiter.NO_LIMIT_PERIOD;

  int refreshPeriod() default PeriodRateLimiter.DEFAULT_REFRESH_PERIOD_SECONDS;

  /**
   * @return time unit for max delay between reconciliations
   */
  TimeUnit refreshPeriodTimeUnit() default TimeUnit.SECONDS;
}
