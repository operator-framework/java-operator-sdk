package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowCleanupResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowExecutionResult;

/**
 * Contextual information related to {@link DependentResource} either to retrieve the actual
 * implementations to interact with them or to pass information between them and/or the reconciler
 */
@SuppressWarnings("rawtypes")
public class DefaultManagedDependentResourceContext implements ManagedDependentResourceContext {

  private WorkflowExecutionResult workflowExecutionResult;
  private WorkflowCleanupResult workflowCleanupResult;
  private final ConcurrentHashMap attributes = new ConcurrentHashMap();

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
  @Override
  public <T> Optional<T> get(Object key, Class<T> expectedType) {
    return Optional.ofNullable(attributes.get(key))
        .filter(expectedType::isInstance)
        .map(expectedType::cast);
  }

  /**
   * Associates the specified contextual value to the specified key. If the value is {@code null},
   * the semantics of this operation is defined as removing the mapping associated with the
   * specified key.
   *
   * @param key the key identifying which contextual object to add or remove from the context
   * @param value the value to add to the context or {@code null} to remove an existing entry
   *        associated with the specified key
   * @return an Optional containing the previous value associated with the key or
   *         {@link Optional#empty()} if none existed
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T put(Object key, T value) {
    if (value == null) {
      return (T) Optional.ofNullable(attributes.remove(key));
    }
    return (T) Optional.ofNullable(attributes.put(key, value));
  }

  /**
   * Retrieves the value associated with the key or fail with an exception if none exists.
   *
   * @param key the key identifying which contextual object to retrieve
   * @param expectedType the expected type of the value to retrieve
   * @param <T> the type of the expected contextual object
   * @return the contextual object value associated with the specified key
   * @see #get(Object, Class)
   */
  @Override
  @SuppressWarnings("unused")
  public <T> T getMandatory(Object key, Class<T> expectedType) {
    return get(key, expectedType).orElseThrow(() -> new IllegalStateException(
        "Mandatory attribute (key: " + key + ", type: " + expectedType.getName()
            + ") is missing or not of the expected type"));
  }

  public DefaultManagedDependentResourceContext setWorkflowExecutionResult(
      WorkflowExecutionResult workflowExecutionResult) {
    this.workflowExecutionResult = workflowExecutionResult;
    return this;
  }

  public DefaultManagedDependentResourceContext setWorkflowCleanupResult(
      WorkflowCleanupResult workflowCleanupResult) {
    this.workflowCleanupResult = workflowCleanupResult;
    return this;
  }

  @Override
  public Optional<WorkflowExecutionResult> getWorkflowExecutionResult() {
    return Optional.ofNullable(workflowExecutionResult);
  }

  @Override
  public Optional<WorkflowCleanupResult> getWorkflowCleanupResult() {
    return Optional.ofNullable(workflowCleanupResult);
  }
}
