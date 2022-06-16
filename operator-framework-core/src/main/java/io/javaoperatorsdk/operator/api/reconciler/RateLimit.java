package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface RateLimit {

  int limitForPeriod();

  int refreshPeriod();

  /**
   * @return time unit for max delay between reconciliations
   */
  TimeUnit refreshPeriodTimeUnit() default TimeUnit.SECONDS;
}
