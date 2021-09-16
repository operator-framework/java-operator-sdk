package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

public final class CustomResourceEventFilters {

  private static final CustomResourceEventFilter<CustomResource> USE_FINALIZER =
      new CustomResourceEventFilter<>() {
        @Override
        public boolean test(
            ControllerConfiguration configuration,
            CustomResource oldResource,
            CustomResource newResource) {

          boolean oldFinalizer = oldResource.hasFinalizer(configuration.getFinalizer());
          boolean newFinalizer = newResource.hasFinalizer(configuration.getFinalizer());

          return !newFinalizer || !oldFinalizer;
        }
      };

  private static final CustomResourceEventFilter<CustomResource> GENERATION_AWARE =
      new CustomResourceEventFilter<>() {
        @Override
        public boolean test(
            ControllerConfiguration configuration,
            CustomResource oldResource,
            CustomResource newResource) {
          return oldResource.getMetadata().getGeneration() < newResource.getMetadata()
              .getGeneration();
        }
      };

  private static final CustomResourceEventFilter<CustomResource> PASSTHROUGH =
      new CustomResourceEventFilter<>() {
        @Override
        public boolean test(
            ControllerConfiguration configuration,
            CustomResource oldResource,
            CustomResource newResource) {
          return true;
        }
      };

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
