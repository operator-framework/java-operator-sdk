package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.*;

import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Workflow {

  Dependent[] dependents();

}
