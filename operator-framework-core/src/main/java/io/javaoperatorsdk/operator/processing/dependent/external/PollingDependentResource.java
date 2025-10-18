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
package io.javaoperatorsdk.operator.processing.dependent.external;

import java.time.Duration;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;

@Ignore
public abstract class PollingDependentResource<R, P extends HasMetadata, ID>
    extends AbstractPollingDependentResource<R, P, ID>
    implements PollingEventSource.GenericResourceFetcher<R> {

  private final CacheKeyMapper<R, ID> cacheKeyMapper;

  public PollingDependentResource(Class<R> resourceType, CacheKeyMapper<R, ID> cacheKeyMapper) {
    super(resourceType);
    this.cacheKeyMapper = cacheKeyMapper;
  }

  public PollingDependentResource(
      Class<R> resourceType, Duration pollingPeriod, CacheKeyMapper<R, ID> cacheKeyMapper) {
    super(resourceType, pollingPeriod);
    this.cacheKeyMapper = cacheKeyMapper;
  }

  @Override
  protected ExternalResourceCachingEventSource<R, P, ID> createEventSource(
      EventSourceContext<P> context) {
    return new PollingEventSource<>(
        resourceType(),
        new PollingConfiguration<>(name(), this, getPollingPeriod(), cacheKeyMapper));
  }
}
