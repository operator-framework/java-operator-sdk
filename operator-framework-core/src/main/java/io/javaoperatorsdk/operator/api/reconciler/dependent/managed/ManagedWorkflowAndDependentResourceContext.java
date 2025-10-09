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
package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowCleanupResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;

/**
 * Contextual information related to {@link DependentResource} either to retrieve the actual
 * implementations to interact with them or to pass information between them and/or the reconciler
 */
public interface ManagedWorkflowAndDependentResourceContext {

  /**
   * Retrieve a contextual object, if it exists and is of the specified expected type, associated
   * with the specified key. Contextual objects can be used to pass data between the reconciler and
   * dependent resources and are scoped to the current reconciliation.
   *
   * @param key the key identifying which contextual object to retrieve
   * @param expectedType the class representing the expected type of the contextual object
   * @param <T> the type of the expected contextual object
   * @return an Optional containing the contextual object or {@link Optional#empty()} if no such
   *     object exists or doesn't match the expected type
   */
  <T> Optional<T> get(Object key, Class<T> expectedType);

  /**
   * Associates the specified contextual value to the specified key. If the value is {@code null},
   * the semantics of this operation is defined as removing the mapping associated with the
   * specified key.
   *
   * <p>Note that, while implementations shouldn't throw a {@link ClassCastException} when the new
   * value type differs from the type of the existing value, calling sites might encounter such
   * exceptions if they bind the return value to a specific type. Users are either expected to
   * disregard the return value (most common case) or "reset" the value type associated with the
   * specified key by first calling {@code put(key, null)} if they want to ensure some level of type
   * safety in their code (where attempting to store values of different types under the same key
   * might be indicative of an issue).
   *
   * @param <T> object type
   * @param key the key identifying which contextual object to add or remove from the context
   * @param value the value to add to the context or {@code null} to remove an existing entry
   *     associated with the specified key
   * @return the previous value if one was associated with the specified key, {@code null}
   *     otherwise.
   */
  <T> T put(Object key, T value);

  /**
   * Retrieves the value associated with the key or fail with an exception if none exists.
   *
   * @param key the key identifying which contextual object to retrieve
   * @param expectedType the expected type of the value to retrieve
   * @param <T> the type of the expected contextual object
   * @return the contextual object value associated with the specified key
   * @see #get(Object, Class)
   */
  @SuppressWarnings("unused")
  <T> T getMandatory(Object key, Class<T> expectedType);

  Optional<WorkflowReconcileResult> getWorkflowReconcileResult();

  @SuppressWarnings("unused")
  Optional<WorkflowCleanupResult> getWorkflowCleanupResult();

  /**
   * Explicitly reconcile the declared workflow for the associated {@link
   * io.javaoperatorsdk.operator.api.reconciler.Reconciler}
   *
   * @return the result of the workflow reconciliation
   * @throws IllegalStateException if called when explicit invocation is not requested
   */
  WorkflowReconcileResult reconcileManagedWorkflow();

  /**
   * Explicitly clean-up dependent resources in the declared workflow for the associated {@link
   * io.javaoperatorsdk.operator.api.reconciler.Reconciler}. Note that calling this method is only
   * needed if the associated reconciler implements the {@link
   * io.javaoperatorsdk.operator.api.reconciler.Cleaner} interface.
   *
   * @return the result of the workflow reconciliation on cleanup
   * @throws IllegalStateException if called when explicit invocation is not requested
   */
  WorkflowCleanupResult cleanupManageWorkflow();
}
