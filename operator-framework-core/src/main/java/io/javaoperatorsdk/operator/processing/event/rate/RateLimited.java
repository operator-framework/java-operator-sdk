package io.javaoperatorsdk.operator.processing.event.rate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface RateLimited {

  int maxReconciliations();

  int within();

  /**
   * @return time unit for max delay between reconciliations
   */
  TimeUnit unit() default TimeUnit.SECONDS;
}
