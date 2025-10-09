/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.event.source.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public class InternalEventFilters {

  private InternalEventFilters() {}

  static <T extends HasMetadata> OnUpdateFilter<T> onUpdateMarkedForDeletion() {
    // the old resource is checked since in corner cases users might still want to update the status
    // for a resource that is marked for deletion

    return (newResource, oldResource) ->
        !oldResource.isMarkedForDeletion() && newResource.isMarkedForDeletion();
  }

  static <T extends HasMetadata> OnUpdateFilter<T> onUpdateGenerationAware(
      boolean generationAware) {

    return (newResource, oldResource) -> {
      if (!generationAware) {
        return true;
      }
      // for example pods don't have generation
      if (oldResource.getMetadata().getGeneration() == null) {
        return true;
      }

      return oldResource.getMetadata().getGeneration() < newResource.getMetadata().getGeneration();
    };
  }

  static <T extends HasMetadata> OnUpdateFilter<T> onUpdateFinalizerNeededAndApplied(
      boolean useFinalizer, String finalizerName) {
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
