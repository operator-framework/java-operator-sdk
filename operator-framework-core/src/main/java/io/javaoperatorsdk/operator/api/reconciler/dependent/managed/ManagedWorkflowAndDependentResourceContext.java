package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowCleanupResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;
import java.util.Optional;

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
   *         object exists or doesn't match the expected type
   */
  <T> Optional<T> get(Object key, Class<T> expectedType);

  /**
   * Associates the specified contextual value to the specified key. If the value is {@code null},
   * the semantics of this operation is defined as removing the mapping associated with the
   * specified key.
   *
   * @param <T> object type
   * @param key the key identifying which contextual object to add or remove from the context
   * @param value the value to add to the context or {@code null} to remove an existing entry
   *        associated with the specified key
   * @return an Optional containing the previous value associated with the key or
   *         {@link Optional#empty()} if none existed
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

  WorkflowReconcileResult getWorkflowReconcileResult();

  @SuppressWarnings("unused")
  WorkflowCleanupResult getWorkflowCleanupResult();

  /**
   * Explicitly reconcile the declared workflow for the associated
   * {@link io.javaoperatorsdk.operator.api.reconciler.Reconciler}
   *
   * @throws IllegalStateException if called when explicit invocation is not requested
   */
  void reconcileManagedWorkflow();

  /**
   * Explicitly clean-up dependent resources in the declared workflow for the associated
   * {@link io.javaoperatorsdk.operator.api.reconciler.Reconciler}. Note that calling this method is
   * only needed if the associated reconciler implements the
   * {@link io.javaoperatorsdk.operator.api.reconciler.Cleaner} interface.
   *
   * @throws IllegalStateException if called when explicit invocation is not requested
   */
  void cleanupManageWorkflow();

}
