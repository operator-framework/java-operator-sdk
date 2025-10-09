/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowCleanupResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Workflow {

  Dependent[] dependents();

  /**
   * If {@code true}, the managed workflow should be explicitly invoked within the reconciler
   * implementation. If {@code false}, the workflow is invoked just before the {@link
   * Reconciler#reconcile(HasMetadata, Context)} method.
   */
  boolean explicitInvocation() default false;

  /**
   * If {@code true} and exceptions are thrown during the workflow's execution, the reconciler won't
   * throw an {@link io.javaoperatorsdk.operator.AggregatedOperatorException} at the end of the
   * execution as would normally be the case. Instead, it will proceed to its {@link
   * Reconciler#reconcile(HasMetadata, Context)} method as if no error occurred. It is then up to
   * the developer to decide how to proceed by retrieving the errored dependents (and their
   * associated exception) via {@link WorkflowReconcileResult#getErroredDependents()} or {@link
   * WorkflowCleanupResult#getErroredDependents()}, the workflow result itself being accessed from
   * {@link Context#managedWorkflowAndDependentResourceContext()}. If {@code false}, an exception
   * will be automatically thrown at the end of the workflow execution, presenting an aggregated
   * view of what happened.
   */
  boolean handleExceptionsInReconciler() default false;
}
