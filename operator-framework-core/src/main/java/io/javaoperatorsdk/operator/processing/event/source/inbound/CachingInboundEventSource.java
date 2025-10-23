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
package io.javaoperatorsdk.operator.processing.event.source.inbound;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.ResourceIDMapper;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;

public class CachingInboundEventSource<R, P extends HasMetadata, ID>
    extends ExternalResourceCachingEventSource<R, P, ID> implements ResourceEventAware<P> {

  private final ResourceFetcher<R, P> resourceFetcher;
  private final Set<ResourceID> fetchedForPrimaries = ConcurrentHashMap.newKeySet();

  public CachingInboundEventSource(
      ResourceFetcher<R, P> resourceFetcher,
      Class<R> resourceClass,
      ResourceIDMapper<R, ID> resourceIDMapper) {
    super(resourceClass, resourceIDMapper);
    this.resourceFetcher = resourceFetcher;
  }

  public void handleResourceEvent(ResourceID primaryID, Set<R> resources) {
    super.handleResources(primaryID, resources);
  }

  public void handleResourceEvent(ResourceID primaryID, R resource) {
    super.handleResources(primaryID, resource);
  }

  public void handleResourceDeleteEvent(ResourceID primaryID, ID resourceID) {
    super.handleDelete(primaryID, Set.of(resourceID));
  }

  @Override
  public void onResourceDeleted(P resource) {
    var resourceID = ResourceID.fromResource(resource);
    fetchedForPrimaries.remove(resourceID);
  }

  private Set<R> getAndCacheResource(P primary) {
    var primaryID = ResourceID.fromResource(primary);
    var values = resourceFetcher.fetchResources(primary);
    handleResources(primaryID, values, false);
    fetchedForPrimaries.add(primaryID);
    return values;
  }

  /**
   * When this event source is queried for the resource, it might not be fully "synced". Thus, the
   * cache might not be propagated, therefore the supplier is checked for the resource too.
   *
   * @param primary resource of the controller
   * @return the related resource for this event source
   */
  @Override
  public Set<R> getSecondaryResources(P primary) {
    var primaryID = ResourceID.fromResource(primary);
    var cachedValue = cache.get(primaryID);
    if (cachedValue != null && !cachedValue.isEmpty()) {
      return new HashSet<>(cachedValue.values());
    } else {
      if (fetchedForPrimaries.contains(primaryID)) {
        return Collections.emptySet();
      } else {
        return getAndCacheResource(primary);
      }
    }
  }

  public interface ResourceFetcher<R, P> {
    Set<R> fetchResources(P primaryResource);
  }
}
