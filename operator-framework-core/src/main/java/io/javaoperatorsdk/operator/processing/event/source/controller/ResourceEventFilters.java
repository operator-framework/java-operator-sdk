package io.javaoperatorsdk.operator.processing.event.source.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ObservedGenerationAware;

/**
 * Convenience implementations of, and utility methods for, {@link ResourceEventFilter}.
 */
public final class ResourceEventFilters {

  private static final ResourceEventFilter<HasMetadata> USE_FINALIZER =
      (configuration, oldResource, newResource) -> {
        if (configuration.useFinalizer()) {
          final var finalizer = configuration.getFinalizer();
          boolean oldFinalizer = oldResource == null || oldResource.hasFinalizer(finalizer);
          boolean newFinalizer = newResource.hasFinalizer(finalizer);

          return !newFinalizer || !oldFinalizer;
        } else {
          return false;
        }
      };

  private static final ResourceEventFilter<HasMetadata> GENERATION_AWARE =
      (configuration, oldResource, newResource) -> {
        final var generationAware = configuration.isGenerationAware();
        // todo: change this to check for HasStatus (or similar) when
        // https://github.com/fabric8io/kubernetes-client/issues/3586 is fixed
        if (newResource instanceof CustomResource<?, ?>) {
          var newCustomResource = (CustomResource<?, ?>) newResource;
          final var status = newCustomResource.getStatus();
          if (generationAware && status instanceof ObservedGenerationAware) {
            var actualGeneration = newResource.getMetadata().getGeneration();
            var observedGeneration = ((ObservedGenerationAware) status)
                .getObservedGeneration();
            return observedGeneration == null || actualGeneration > observedGeneration;
          }
        }
        return oldResource == null || !generationAware ||
            oldResource.getMetadata().getGeneration() < newResource.getMetadata().getGeneration();
      };

  private static final ResourceEventFilter<HasMetadata> PASSTHROUGH =
      (configuration, oldResource, newResource) -> true;

  private static final ResourceEventFilter<HasMetadata> NONE =
      (configuration, oldResource, newResource) -> false;

  private static final ResourceEventFilter<HasMetadata> MARKED_FOR_DELETION =
      (configuration, oldResource, newResource) -> newResource.isMarkedForDeletion();

  private ResourceEventFilters() {}

  /**
   * Retrieves a filter that accepts all events.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter that accepts all events
   */
  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata> ResourceEventFilter<T> passthrough() {
    return (ResourceEventFilter<T>) PASSTHROUGH;
  }

  /**
   * Retrieves a filter that reject all events.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter that reject all events
   */
  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata> ResourceEventFilter<T> none() {
    return (ResourceEventFilter<T>) NONE;
  }

  /**
   * Retrieves a filter that accepts all events if generation-aware processing is not activated but
   * only changes that represent a generation increase otherwise.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter accepting changes based on generation information
   */
  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata> ResourceEventFilter<T> generationAware() {
    return (ResourceEventFilter<T>) GENERATION_AWARE;
  }

  /**
   * Retrieves a filter that accepts changes if the target controller uses a finalizer and that
   * finalizer hasn't already been applied, rejecting them otherwise.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter accepting changes based on whether the finalizer is needed and has been
   *         applied
   */
  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata> ResourceEventFilter<T> finalizerNeededAndApplied() {
    return (ResourceEventFilter<T>) USE_FINALIZER;
  }

  /**
   * Retrieves a filter that accepts changes if the custom resource is marked for deletion.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter accepting changes based on whether the Custom Resource is marked for deletion.
   */
  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata> ResourceEventFilter<T> markedForDeletion() {
    return (ResourceEventFilter<T>) MARKED_FOR_DELETION;
  }

  /**
   * Combines the provided, potentially {@code null} filters with an AND logic, i.e. the resulting
   * filter will only accept the change if all filters accept it, reject it otherwise.
   * <p>
   * Note that the evaluation of filters is lazy: the result is returned as soon as possible without
   * evaluating all filters if possible.
   *
   * @param items the filters to combine
   * @param <T> the type of custom resources the filters are supposed to handle
   * @return a combined filter implementing the AND logic combination of the provided filters
   */
  @SafeVarargs
  public static <T extends HasMetadata> ResourceEventFilter<T> and(
      ResourceEventFilter<T>... items) {
    if (items == null) {
      return none();
    }

    return (configuration, oldResource, newResource) -> {
      for (ResourceEventFilter<T> item : items) {
        if (item == null) {
          continue;
        }

        if (!item.acceptChange(configuration, oldResource, newResource)) {
          return false;
        }
      }

      return true;
    };
  }

  /**
   * Combines the provided, potentially {@code null} filters with an OR logic, i.e. the resulting
   * filter will accept the change if any of the filters accepts it, rejecting it only if all reject
   * it.
   * <p>
   * Note that the evaluation of filters is lazy: the result is returned as soon as possible without
   * evaluating all filters if possible.
   *
   * @param items the filters to combine
   * @param <T> the type of custom resources the filters are supposed to handle
   * @return a combined filter implementing the OR logic combination of both provided filters
   */
  @SafeVarargs
  public static <T extends HasMetadata> ResourceEventFilter<T> or(
      ResourceEventFilter<T>... items) {
    if (items == null) {
      return none();
    }

    return (configuration, oldResource, newResource) -> {
      for (ResourceEventFilter<T> item : items) {
        if (item == null) {
          continue;
        }

        if (item.acceptChange(configuration, oldResource, newResource)) {
          return true;
        }
      }

      return false;
    };
  }
}
