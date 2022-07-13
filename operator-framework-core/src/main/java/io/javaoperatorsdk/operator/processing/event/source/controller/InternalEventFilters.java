package io.javaoperatorsdk.operator.processing.event.source.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public class InternalEventFilters {

  private InternalEventFilters() {}

  static <T extends HasMetadata> OnUpdateFilter<T> onUpdateMarkedForDeletion() {
    return (newResource, oldResource) -> newResource.isMarkedForDeletion();
  }

  static <T extends HasMetadata> OnUpdateFilter<T> onUpdateGenerationAware(
      boolean generationAware) {

    return (newResource, oldResource) -> {
      if (!generationAware) {
        return true;
      }
      return oldResource.getMetadata().getGeneration() < newResource
          .getMetadata().getGeneration();
    };
  }

  static <T extends HasMetadata> OnUpdateFilter<T> onUpdateFinalizerNeededAndApplied(
      boolean useFinalizer,
      String finalizerName) {
    return (newResource, oldResource) -> {
      if (useFinalizer) {
        boolean oldFinalizer = oldResource.hasFinalizer(finalizerName);
        boolean newFinalizer = newResource.hasFinalizer(finalizerName);
        // accepts event if old did not have finalizer, since it was just added, so the event needs
        // to
        // be published.
        return !newFinalizer || !oldFinalizer;
      } else {
        return false;
      }
    };
  }
}
