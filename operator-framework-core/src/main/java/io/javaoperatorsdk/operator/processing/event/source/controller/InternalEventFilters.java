package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.function.BiPredicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.Controller;

public class InternalEventFilters {

  // todo unit tests

  static <T extends HasMetadata> BiPredicate<T, T> onUpdateMarkedForDeletion() {
    return (newResource, oldResource) -> newResource.isMarkedForDeletion();
  }

  static <T extends HasMetadata> BiPredicate<T, T> onUpdateGenerationAware(
      boolean generationAware) {

    return (newResource, oldResource) -> {
      if (!generationAware) {
        return true;
      }
      return oldResource.getMetadata().getGeneration() < newResource
          .getMetadata().getGeneration();
    };
  }

  static <T extends HasMetadata> BiPredicate<T, T> onUpdateFinalizerNeededAndApplied(
      Controller<T> controller) {
    return (newResource, oldResource) -> {
      if (controller.useFinalizer()) {
        final var finalizer = controller.getConfiguration().getFinalizerName();
        boolean oldFinalizer = oldResource.hasFinalizer(finalizer);
        boolean newFinalizer = newResource.hasFinalizer(finalizer);
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
