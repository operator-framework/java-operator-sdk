package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

/**
 * Contextual information related to {@link DependentResource} either to retrieve the actual
 * implementations to interact with them or to pass information between them and/or the reconciler
 */
@SuppressWarnings("rawtypes")
public class ManagedDependentResourceContext {

  private final Map<String, ReconcileResult> reconcileResults = new ConcurrentHashMap<>();
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
  @SuppressWarnings("unchecked")
  public Optional put(Object key, Object value) {
    if (value == null) {
      return Optional.ofNullable(attributes.remove(key));
    }
    return Optional.ofNullable(attributes.put(key, value));
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
  @SuppressWarnings("unused")
  public <T> T getMandatory(Object key, Class<T> expectedType) {
    return get(key, expectedType).orElseThrow(() -> new IllegalStateException(
        "Mandatory attribute (key: " + key + ", type: " + expectedType.getName()
            + ") is missing or not of the expected type"));
  }

  /**
   * Retrieve the {@link ReconcileResult}, if it exists, associated with the
   * {@link DependentResource} associated with the specified name
   *
   * @param name the name of the {@link DependentResource} for which we want to retrieve a
   *        {@link ReconcileResult}
   * @return an Optional containing the reconcile result or {@link Optional#empty()} if no such
   *         result is available
   */
  @SuppressWarnings({"rawtypes", "unused"})
  public Optional<ReconcileResult> getReconcileResult(String name) {
    return Optional.ofNullable(reconcileResults.get(name));
  }

  /**
   * Set the {@link ReconcileResult} for the specified {@link DependentResource} implementation.
   *
   * @param name the name of the {@link DependentResource} for which we want to set the
   *        {@link ReconcileResult}
   * @param reconcileResult the reconcile result associated with the specified
   *        {@link DependentResource}
   */
  public void setReconcileResult(String name, ReconcileResult reconcileResult) {
    reconcileResults.put(name, reconcileResult);
  }
}
