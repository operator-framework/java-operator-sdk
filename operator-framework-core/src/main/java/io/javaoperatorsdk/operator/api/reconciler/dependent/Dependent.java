package io.javaoperatorsdk.operator.api.reconciler.dependent;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.EMPTY_STRING;

public @interface Dependent {

  @SuppressWarnings("rawtypes")
  Class<? extends DependentResource> type();

  String name() default EMPTY_STRING;
}
