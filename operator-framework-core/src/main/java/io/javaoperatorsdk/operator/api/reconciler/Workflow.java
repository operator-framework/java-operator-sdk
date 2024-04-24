package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Workflow {

  Dependent[] dependents();

  /**
   * If true, managed workflow should be explicitly invoked within the reconciler implementation. If
   * false workflow is invoked just before the {@link Reconciler#reconcile(HasMetadata, Context)}
   * method.
   */
  boolean explicitInvocation() default false;

  /**
   * if true and an exception(s) is thrown in the managed workflow, when the workflow reconciliation
   * finished the controller won't throw an exception, instead will call the reconcile method where
   * the reconcile result is accessible through
   * {@link Context#managedWorkflowAndDependentResourceContext()}. If false, the exception is thrown
   * by the controller before executing the reconcile method.
   */
  boolean silentExceptionHandling() default false;

}
