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
package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Maps secondary resource to primary resources.
 *
 * @param <R> secondary resource type
 */
@FunctionalInterface
public interface SecondaryToPrimaryMapper<R> {

  /**
   * Maps a secondary resource to the set of primary resources that should be reconciled in
   * response. Implementing this single-argument form is sufficient for the vast majority of use
   * cases — prefer it unless you specifically need access to the previous version of the secondary
   * resource (see {@link #toPrimaryResourceIDs(Object, Object)}).
   *
   * @param resource the secondary resource for which an event was received
   * @return set of primary resource IDs to enqueue for reconciliation; an empty set means the event
   *     is irrelevant and no reconciliation is triggered
   */
  Set<ResourceID> toPrimaryResourceIDs(R resource);

  /**
   * Variant invoked by the framework for every secondary resource event, providing both the new and
   * the previous version of the resource (when available). The default implementation simply
   * delegates to {@link #toPrimaryResourceIDs(Object)} and ignores {@code oldResource}, so existing
   * mappers keep working unchanged.
   *
   * <p>Override this method only for edge cases where the set of primary resources to reconcile
   * depends on what changed between the old and the new version of the secondary resource (for
   * example, when a reference held by the secondary resource has moved from one primary to another
   * and both primaries need to be reconciled).
   *
   * <p><strong>Use with caution.</strong> {@code oldResource} is sourced from the informer cache
   * and is therefore only populated for genuine update events observed while the controller is
   * already running. In particular, when the controller starts up, the cache is empty and the
   * initial events received for resources that already existed in the cluster are delivered as adds
   * with {@code oldResource == null} (even if those resources had been updated previously). {@code
   * oldResource} is also {@code null} for delete events and for events triggered through the
   * primary-to-secondary index.
   *
   * <p>Implementations must therefore handle a {@code null} {@code oldResource} gracefully and not
   * rely on it being present for correctness — overriding this method is intended for edge cases
   * only. Genericly speaking controller should also handle such change checking during
   * reconciliation, so when controller starts and event is missed it is properly reconiled.
   *
   * @param newResource the current version of the secondary resource
   * @param oldResource the previous version of the secondary resource, or {@code null} if not
   *     available (see above)
   * @return set of primary resource IDs to enqueue for reconciliation
   */
  default Set<ResourceID> toPrimaryResourceIDs(R newResource, R oldResource) {
    return toPrimaryResourceIDs(newResource);
  }
}
