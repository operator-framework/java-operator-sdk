package io.javaoperatorsdk.operator.sample.dependent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface SchemaConfig {
  int DEFAULT_POLL_PERIOD = 500;
  int DEFAULT_PORT = 3306;

  int pollPeriod() default DEFAULT_POLL_PERIOD;

  String host();

  String user();

  String password();

  int port() default DEFAULT_PORT;
}
