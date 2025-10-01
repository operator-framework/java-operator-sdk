package io.javaoperatorsdk.operator.processing.dependent;

/**
 * Provides the identifier for an object that represents an external resource. This ID is used to
 * select target resource for a dependent resource from the resources returned by `{@link
 * io.javaoperatorsdk.operator.api.reconciler.Context#getSecondaryResources(Class)}`.
 *
 * @param <T>
 */
public interface ExternalDependentIDProvider<T> {

  T externalResourceId();
}
