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
   * response.
   *
   * @param resource the secondary resource for which an event was received
   * @return set of primary resource IDs to enqueue for reconciliation; an empty set means the event
   *     is irrelevant and no reconciliation is triggered. This is called also the old and the new
   *     resources, in case of an update.
   */
  Set<ResourceID> toPrimaryResourceIDs(R resource);
}
