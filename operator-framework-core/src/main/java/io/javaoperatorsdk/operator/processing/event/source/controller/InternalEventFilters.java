package io.javaoperatorsdk.operator.processing.event.source.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class InternalEventFilters {

  private InternalEventFilters() {}

  static <T extends HasMetadata> boolean onUpdateMarkedForDeletion(T oldResource, T newResource) {
    return newResource.isMarkedForDeletion();
  }

  static <T extends HasMetadata> boolean onUpdateGenerationAware(boolean generationAware,
      T oldResource, T newResource) {
    if (!generationAware) {
      return true;
    }
    return oldResource.getMetadata().getGeneration() < newResource
        .getMetadata().getGeneration();
  }

  static <T extends HasMetadata> boolean onUpdateFinalizerNeededAndApplied(boolean useFinalizer,
      String finalizerName, T oldResource, T newResource) {
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
  }
}
