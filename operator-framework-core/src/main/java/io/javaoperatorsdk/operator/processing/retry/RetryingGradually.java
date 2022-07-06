package io.javaoperatorsdk.operator.processing.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RetryingGradually {

  int DEFAULT_MAX_ATTEMPTS = 5;
  long DEFAULT_INITIAL_INTERVAL = 2000L;
  double DEFAULT_MULTIPLIER = 1.5D;

  long DEFAULT_MAX_INTERVAL = (long) (RetryingGradually.DEFAULT_INITIAL_INTERVAL * Math.pow(
      RetryingGradually.DEFAULT_MULTIPLIER, RetryingGradually.DEFAULT_MAX_ATTEMPTS));

  long UNSET_VALUE = Long.MAX_VALUE;

  int maxAttempts() default DEFAULT_MAX_ATTEMPTS;

  long initialInterval() default DEFAULT_INITIAL_INTERVAL;

  double intervalMultiplier() default DEFAULT_MULTIPLIER;

  long maxInterval() default UNSET_VALUE;
}
