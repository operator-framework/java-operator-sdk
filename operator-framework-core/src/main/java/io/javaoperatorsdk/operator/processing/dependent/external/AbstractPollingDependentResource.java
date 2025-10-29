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
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.processing.dependent.AbstractExternalDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;

@Ignore
public abstract class AbstractPollingDependentResource<R, P extends HasMetadata>
    extends AbstractExternalDependentResource<R, P, ExternalResourceCachingEventSource<R, P>>
    implements CacheKeyMapper<R> {

  public static final Duration DEFAULT_POLLING_PERIOD = Duration.ofMillis(5000);
  private Duration pollingPeriod;

  protected AbstractPollingDependentResource() {}

  protected AbstractPollingDependentResource(Class<R> resourceType) {
    this(resourceType, DEFAULT_POLLING_PERIOD);
  }

  public AbstractPollingDependentResource(Class<R> resourceType, Duration pollingPeriod) {
    super(resourceType);
    this.pollingPeriod = pollingPeriod;
  }

  public void setPollingPeriod(Duration pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
  }

  public Duration getPollingPeriod() {
    return pollingPeriod;
  }

  // for now dependent resources support event sources only with one owned resource.
  @Override
  public String keyFor(R resource) {
    return CacheKeyMapper.singleResourceCacheKeyMapper().keyFor(resource);
  }
}
