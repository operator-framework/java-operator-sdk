package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.CustomResource;

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

  @SuppressWarnings("unchecked")
  public static <T extends CustomResource> CustomResourceEventFilter<T> passthrough() {
    return (CustomResourceEventFilter<T>) PASSTHROUGH;
  }

  @SuppressWarnings("unchecked")
  public static <T extends CustomResource> CustomResourceEventFilter<T> generationAware() {
    return (CustomResourceEventFilter<T>) GENERATION_AWARE;
  }

  @SuppressWarnings("unchecked")
  public static <T extends CustomResource> CustomResourceEventFilter<T> useFinalizer() {
    return (CustomResourceEventFilter<T>) USE_FINALIZER;
  }

  public static <T extends CustomResource<?, ?>> CustomResourceEventFilter<T> and(
      CustomResourceEventFilter<T> first, CustomResourceEventFilter<T> second) {
    return first == null ? (second == null ? passthrough() : second) : first.and(second);
  }

  public static <T extends CustomResource<?, ?>> CustomResourceEventFilter<T> or(
      CustomResourceEventFilter<T> first, CustomResourceEventFilter<T> second) {
    return first == null ? (second == null ? passthrough() : second) : first.or(second);
  }
}
