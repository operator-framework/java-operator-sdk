package io.javaoperatorsdk.operator.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RetryConfiguration {

  int DEFAULT_MAX_ATTEMPTS = 5;
  long DEFAULT_INITIAL_INTERVAL = 2000L;
  double DEFAULT_MULTIPLIER = 1.5D;

  int maxAttempts() default DEFAULT_MAX_ATTEMPTS;

  long initialInterval() default DEFAULT_INITIAL_INTERVAL;

  double intervalMultiplier() default DEFAULT_MULTIPLIER;

  long maxInterval() default -1;
}
