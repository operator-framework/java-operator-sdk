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
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

class DefaultPrimaryToSecondaryIndex<R extends HasMetadata> implements PrimaryToSecondaryIndex<R> {

  private final SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
  private final Map<ResourceID, Set<ResourceID>> index = new HashMap<>();

  public DefaultPrimaryToSecondaryIndex(SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper) {
    this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
  }

  @Override
  public synchronized Set<ResourceID> onAddOrUpdate(R resource, R oldResource) {

    Set<ResourceID> primaryResources = secondaryToPrimaryMapper.toPrimaryResourceIDs(resource);

    var secondaryId = ResourceID.fromResource(resource);

    primaryResources.forEach(
        primaryResource -> {
          var resourceSet =
              index.computeIfAbsent(primaryResource, pr -> ConcurrentHashMap.newKeySet());
          resourceSet.add(secondaryId);
        });

    if (oldResource != null) {
      var obsoletePrimaries =
          new HashSet<>(secondaryToPrimaryMapper.toPrimaryResourceIDs(oldResource));
      if (!primaryResources.containsAll(obsoletePrimaries)) {
        var result = new HashSet<>(primaryResources);
        obsoletePrimaries.removeAll(primaryResources);
        obsoletePrimaries.forEach(
            p ->
                index.computeIfPresent(
                    p,
                    (id, currentSet) -> {
                      currentSet.remove(secondaryId);
                      return currentSet.isEmpty() ? null : currentSet;
                    }));
        result.addAll(obsoletePrimaries);
        return result;
      }
    }
    return primaryResources;
  }

  @Override
  public synchronized Set<ResourceID> onDelete(R resource) {
    Set<ResourceID> primaryResources = secondaryToPrimaryMapper.toPrimaryResourceIDs(resource);
    primaryResources.forEach(
        primaryResource -> {
          var secondaryResources = index.get(primaryResource);
          // this can be null in just very special cases, like when the secondaryToPrimaryMapper is
          // changing dynamically. Like if a list of ResourceIDs mapped dynamically extended in the
          // mapper between the onAddOrUpdate and onDelete is called.
          if (secondaryResources != null) {
            secondaryResources.remove(ResourceID.fromResource(resource));
            if (secondaryResources.isEmpty()) {
              index.remove(primaryResource);
            }
          }
        });
    return primaryResources;
  }

  @Override
  public synchronized Set<ResourceID> getSecondaryResources(ResourceID primary) {
    var resourceIDs = index.get(primary);
    if (resourceIDs == null) {
      return Collections.emptySet();
    } else {
      return Collections.unmodifiableSet(resourceIDs);
    }
  }
}
