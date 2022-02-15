package io.javaoperatorsdk.operator.sample;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ResourcePoller {

  int pollPeriod() default 500;

}
