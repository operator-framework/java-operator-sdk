package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Workflow {

  Dependent[] dependents();

  // todo maybe better naming? "explicitReconciliation" ?
  /**
   * If true, managed workflow should be explicitly invoked within the reconciler implementation. If
   * false workflow is invoked just before the {@link Reconciler#reconcile(HasMetadata, Context)}
   * method.
   */
  boolean explicitInvocation() default false;

}
