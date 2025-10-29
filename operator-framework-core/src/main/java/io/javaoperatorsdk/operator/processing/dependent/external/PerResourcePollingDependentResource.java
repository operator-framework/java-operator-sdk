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
import io.javaoperatorsdk.operator.processing.ResourceIDMapper;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingConfigurationBuilder;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;

@Ignore
public abstract class PerResourcePollingDependentResource<R, P extends HasMetadata, ID>
    extends AbstractPollingDependentResource<R, P, ID>
    implements PerResourcePollingEventSource.ResourceFetcher<R, P> {

  protected PerResourcePollingDependentResource() {}

  protected PerResourcePollingDependentResource(
      Class<R> resourceType, ResourceIDMapper<R, ID> resourceIDMapper) {
    super(resourceType);
    setResourceIDMapper(resourceIDMapper);
  }

  protected PerResourcePollingDependentResource(Class<R> resourceType) {
    super(resourceType);
  }

  protected PerResourcePollingDependentResource(Class<R> resourceType, Duration pollingPeriod) {
    super(resourceType, pollingPeriod);
  }

  protected PerResourcePollingDependentResource(
      Class<R> resourceType, Duration pollingPeriod, ResourceIDMapper<R, ID> resourceIDMapper) {
    super(resourceType, pollingPeriod);
    setResourceIDMapper(resourceIDMapper);
  }

  @Override
  protected ExternalResourceCachingEventSource<R, P, ID> createEventSource(
      EventSourceContext<P> context) {

    return new PerResourcePollingEventSource<>(
        resourceType(),
        context,
        new PerResourcePollingConfigurationBuilder<R, P, ID>(this, getPollingPeriod())
            .withResourceIDMapper(resourceIDMapper())
            .withName(name())
            .build());
  }
}
