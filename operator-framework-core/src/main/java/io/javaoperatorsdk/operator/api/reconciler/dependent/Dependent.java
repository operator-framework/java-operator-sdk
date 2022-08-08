package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

public @interface Dependent {

  @SuppressWarnings("rawtypes")
  Class<? extends DependentResource> type();

  String name() default NO_VALUE_SET;

  Class<? extends Condition> readyPostcondition() default Condition.class;

  Class<? extends Condition> reconcilePrecondition() default Condition.class;

  Class<? extends Condition> deletePostcondition() default Condition.class;

  String[] dependsOn() default {};
}
