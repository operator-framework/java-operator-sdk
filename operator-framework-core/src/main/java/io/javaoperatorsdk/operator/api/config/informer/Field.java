package io.javaoperatorsdk.operator.api.config.informer;

public @interface Field {

  String path();

  String value();

  boolean negate() default false;
}
