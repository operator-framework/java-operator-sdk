package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

/**
 * Contextual information related to {@link DependentResource} either to retrieve the actual
 * implementations to interact with them or to pass information between them and/or the reconciler
 */
@SuppressWarnings("rawtypes")
public class ManagedDependentResourceContext {

  private final Map<String, DependentResource> dependentResources;
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
  public <T> T getMandatory(Object key, Class<T> expectedType) {
    return get(key, expectedType).orElseThrow(() -> new IllegalStateException(
        "Mandatory attribute (key: " + key + ", type: " + expectedType.getName()
            + ") is missing or not of the expected type"));
  }

  public ManagedDependentResourceContext(Map<String, DependentResource> dependentResources) {
    this.dependentResources = dependentResources;
  }

  /**
   * Retrieve all the known {@link DependentResource} implementations
   *
   * @return known {@link DependentResource} implementations keyed by their name
   */
  public Map<String, DependentResource> getDependentResources() {
    return dependentResources;
  }

  /**
   * Retrieve the dependent resource implementation associated with the specified resource type.
   *
   * @param name the name of the {@link DependentResource} implementation we want to retrieve
   * @param resourceClass the resource class for which we want to retrieve the associated dependent
   *        resource implementation
   * @param <T> the type of the resources for which we want to retrieve the associated dependent
   *        resource implementation
   * @return the associated dependent resource implementation if it exists or an exception if it
   *         doesn't or several implementations are associated with the specified resource type
   */
  @SuppressWarnings({"unchecked"})
  public <T> DependentResource<T, ?> getDependentResource(String name, Class<T> resourceClass) {
    var dependentResource = dependentResources.get(name);
    if (dependentResource == null) {
      throw new OperatorException("No dependent resource found with name: " + name);
    }
    final var actual = dependentResource.resourceType();
    if (!actual.equals(resourceClass)) {
      throw new OperatorException(
          "Dependent resource implementation doesn't match expected resource type, was: "
              + actual.getName() + " instead of: " + resourceClass.getName());
    }
    return dependentResource;
  }
}
