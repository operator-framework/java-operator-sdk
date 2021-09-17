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
          boolean oldFinalizer = oldResource.hasFinalizer(finalizer);
          boolean newFinalizer = newResource.hasFinalizer(finalizer);

          return !newFinalizer || !oldFinalizer;
        } else {
          return false;
        }
      };

  private static final CustomResourceEventFilter<CustomResource> GENERATION_AWARE =
      (configuration, oldResource, newResource) -> !configuration.isGenerationAware()
          || oldResource.getMetadata().getGeneration() < newResource.getMetadata().getGeneration();

  private static final CustomResourceEventFilter<CustomResource> PASSTHROUGH =
      (configuration, oldResource, newResource) -> true;

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
  public static <T extends CustomResource> CustomResourceEventFilter<T> useFinalizer() {
    return (CustomResourceEventFilter<T>) USE_FINALIZER;
  }

  /**
   * Combines both provided, potentially {@code null} filters with an AND logic, i.e. the resulting
   * filter will only accept the change if both filters accept it, reject it otherwise.
   *
   * Note that the evaluation of filters is lazy: the result is returned as soon as possible without
   * evaluating all filters if possible.
   * 
   * @param first the first filter to combine
   * @param second the second filter to combine
   * @param <T> the type of custom resources the filters are supposed to handle
   * @return a combined filter implementing the AND logic combination of both provided filters
   */
  public static <T extends CustomResource<?, ?>> CustomResourceEventFilter<T> and(
      CustomResourceEventFilter<T> first, CustomResourceEventFilter<T> second) {
    return first == null ? (second == null ? passthrough() : second) : first.and(second);
  }

  /**
   * Combines both provided, potentially {@code null} filters with an OR logic, i.e. the resulting
   * filter will accept the change if any of the filters accepts it, rejecting it only if both
   * reject it.
   * <p>
   * Note that the evaluation of filters is lazy: the result is returned as soon as possible without
   * evaluating all filters if possible.
   *
   * @param first the first filter to combine
   * @param second the second filter to combine
   * @param <T> the type of custom resources the filters are supposed to handle
   * @return a combined filter implementing the OR logic combination of both provided filters
   */
  public static <T extends CustomResource<?, ?>> CustomResourceEventFilter<T> or(
      CustomResourceEventFilter<T> first, CustomResourceEventFilter<T> second) {
    return first == null ? (second == null ? passthrough() : second) : first.or(second);
  }
}
