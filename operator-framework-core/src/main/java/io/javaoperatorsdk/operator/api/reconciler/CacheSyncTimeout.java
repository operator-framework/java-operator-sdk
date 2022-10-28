package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface CacheSyncTimeout {

  int DEFAULT_TIMEOUT = 2;

  int timeout();

  TimeUnit timeUnit() default TimeUnit.MINUTES;

}
