package io.javaoperatorsdk.operator.processing.event.source.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ObservedGenerationAware;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;

/**
 * Convenience implementations of, and utility methods for, {@link ResourceEventFilter}.
 */
public final class ResourceEventFilters {

  private static final ResourceEventFilter<HasMetadata, ControllerConfiguration<HasMetadata>> USE_FINALIZER =
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

  private static final ResourceEventFilter<HasMetadata, ControllerConfiguration<HasMetadata>> GENERATION_AWARE =
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

  private static final ResourceEventFilter PASSTHROUGH =
      (configuration, oldResource, newResource) -> true;

  private static final ResourceEventFilter NONE =
      (configuration, oldResource, newResource) -> false;

  private static final ResourceEventFilter MARKED_FOR_DELETION =
      (configuration, oldResource, newResource) -> newResource.isMarkedForDeletion();

  private ResourceEventFilters() {}

  /**
   * Retrieves a filter that accepts all events.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter that accepts all events
   */
  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata, U extends ResourceConfiguration<T, U>> ResourceEventFilter<T, U> passthrough() {
    return (ResourceEventFilter<T, U>) PASSTHROUGH;
  }

  /**
   * Retrieves a filter that reject all events.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter that reject all events
   */
  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata, U extends ResourceConfiguration<T, U>> ResourceEventFilter<T, U> none() {
    return (ResourceEventFilter<T, U>) NONE;
  }

  /**
   * Retrieves a filter that accepts all events if generation-aware processing is not activated but
   * only changes that represent a generation increase otherwise.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter accepting changes based on generation information
   */
  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata, U extends ResourceConfiguration<T, U>> ResourceEventFilter<T, U> generationAware() {
    return (ResourceEventFilter<T, U>) GENERATION_AWARE;
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
  public static <T extends HasMetadata, U extends ResourceConfiguration<T, U>> ResourceEventFilter<T, U> finalizerNeededAndApplied() {
    return (ResourceEventFilter<T, U>) USE_FINALIZER;
  }

  /**
   * Retrieves a filter that accepts changes if the custom resource is marked for deletion.
   *
   * @param <T> the type of custom resource the filter should handle
   * @return a filter accepting changes based on whether the Custom Resource is marked for deletion.
   */
  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata, U extends ResourceConfiguration<T, U>> ResourceEventFilter<T, U> markedForDeletion() {
    return (ResourceEventFilter<T, U>) MARKED_FOR_DELETION;
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
  public static <T extends HasMetadata, U extends ResourceConfiguration<T, U>> ResourceEventFilter<T, U> and(
      ResourceEventFilter<T, U>... items) {
    if (items == null) {
      return none();
    }

    return (configuration, oldResource, newResource) -> {
      for (ResourceEventFilter<T, U> item : items) {
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
  public static <T extends HasMetadata, U extends ResourceConfiguration<T, U>> ResourceEventFilter<T, U> or(
      ResourceEventFilter<T, U>... items) {
    if (items == null) {
      return none();
    }

    return (configuration, oldResource, newResource) -> {
      for (ResourceEventFilter<T, U> item : items) {
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
