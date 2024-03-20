package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import java.lang.annotation.*;

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

}
