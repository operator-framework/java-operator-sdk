package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

public @interface Dependent {

  @SuppressWarnings("rawtypes")
  Class<? extends DependentResource> type();

  String name() default NO_VALUE_SET;

  @SuppressWarnings("rawtypes")
  Class<? extends Condition> readyPostcondition() default VoidCondition.class;

  @SuppressWarnings("rawtypes")
  Class<? extends Condition> reconcilePrecondition() default VoidCondition.class;

  @SuppressWarnings("rawtypes")
  Class<? extends Condition> deletePostcondition() default VoidCondition.class;

  String[] dependsOn() default {};
}
