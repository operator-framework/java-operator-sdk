package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.CustomResource;

/**
 * Convenience implementations of, and utility methods for, {@link CustomResourceEventFilter}.
 */
public final class CustomResourceEventFilters {

  private static final CustomResourceEventFilter<CustomResource> USE_FINALIZER =
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

  private static final CustomResourceEventFilter<CustomResource> GENERATION_AWARE =
      (configuration, oldResource, newResource) -> oldResource == null
          || !configuration.isGenerationAware()
          || oldResource.getMetadata().getGeneration() < newResource.getMetadata().getGeneration();

  private static final CustomResourceEventFilter<CustomResource> PASSTHROUGH =
      (configuration, oldResource, newResource) -> true;

  private static final CustomResourceEventFilter<CustomResource> NONE =
      (configuration, oldResource, newResource) -> false;

  private static final CustomResourceEventFilter<CustomResource> MARKED_FOR_DELETION =
      (configuration, oldResource, newResource) -> newResource.isMarkedForDeletion();

  private CustomResourceEventFilters() {}

  /**
   * Retrieves a filter that accepts all events.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter that accepts all events
   */
  @SuppressWarnings("unchecked")
  public static <T extends CustomResource> CustomResourceEventFilter<T> passthrough() {
    return (CustomResourceEventFilter<T>) PASSTHROUGH;
  }

  /**
   * Retrieves a filter that reject all events.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter that reject all events
   */
  @SuppressWarnings("unchecked")
  public static <T extends CustomResource> CustomResourceEventFilter<T> none() {
    return (CustomResourceEventFilter<T>) NONE;
  }

  /**
   * Retrieves a filter that accepts all events if generation-aware processing is not activated but
   * only changes that represent a generation increase otherwise.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter accepting changes based on generation information
   */
  @SuppressWarnings("unchecked")
  public static <T extends CustomResource> CustomResourceEventFilter<T> generationAware() {
    return (CustomResourceEventFilter<T>) GENERATION_AWARE;
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
  public static <T extends CustomResource> CustomResourceEventFilter<T> finalizerNeededAndApplied() {
    return (CustomResourceEventFilter<T>) USE_FINALIZER;
  }

  /**
   * Retrieves a filter that accepts changes if the custom resource is marked for deletion.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter accepting changes based on whether the Custom Resource is marked for deletion.
   */
  @SuppressWarnings("unchecked")
  public static <T extends CustomResource> CustomResourceEventFilter<T> markedForDeletion() {
    return (CustomResourceEventFilter<T>) MARKED_FOR_DELETION;
  }

  /**
   * Combines the provided, potentially {@code null} filters with an AND logic, i.e. the resulting
   * filter will only accept the change if all filters accept it, reject it otherwise.
   *
   * Note that the evaluation of filters is lazy: the result is returned as soon as possible without
   * evaluating all filters if possible.
   *
   * @param items the filters to combine
   * @param <T> the type of custom resources the filters are supposed to handle
   * @return a combined filter implementing the AND logic combination of the provided filters
   */
  @SafeVarargs
  public static <T extends CustomResource<?, ?>> CustomResourceEventFilter<T> and(
      CustomResourceEventFilter<T>... items) {
    if (items == null) {
      return none();
    }

    return (configuration, oldResource, newResource) -> {
      for (CustomResourceEventFilter<T> item : items) {
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
  public static <T extends CustomResource<?, ?>> CustomResourceEventFilter<T> or(
      CustomResourceEventFilter<T>... items) {
    if (items == null) {
      return none();
    }

    return (configuration, oldResource, newResource) -> {
      for (CustomResourceEventFilter<T> item : items) {
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
