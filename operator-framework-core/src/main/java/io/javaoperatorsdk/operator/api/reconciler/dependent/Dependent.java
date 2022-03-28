package io.javaoperatorsdk.operator.api.reconciler.dependent;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

public @interface Dependent {

  @SuppressWarnings("rawtypes")
  Class<? extends DependentResource> type();

  String name() default NO_VALUE_SET;
}
