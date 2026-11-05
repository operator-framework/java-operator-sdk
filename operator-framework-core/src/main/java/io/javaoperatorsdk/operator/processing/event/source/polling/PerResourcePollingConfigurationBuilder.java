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
package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.ResourceIDMapper;

public final class PerResourcePollingConfigurationBuilder<R, P extends HasMetadata, ID> {

  private final Duration defaultPollingPeriod;
  private final PerResourcePollingEventSource.ResourceFetcher<R, P> resourceFetcher;

  private String name;
  private Predicate<P> registerPredicate;
  private ScheduledExecutorService executorService;
  private ResourceIDMapper<R, ID> resourceIDMapper;

  public PerResourcePollingConfigurationBuilder(
      PerResourcePollingEventSource.ResourceFetcher<R, P> resourceFetcher,
      Duration defaultPollingPeriod) {
    this.resourceFetcher = resourceFetcher;
    this.defaultPollingPeriod = defaultPollingPeriod;
  }

  /**
   * Runs the polls on the specified executor instead of the one the event source creates by
   * default. Note that an explicitly provided executor is not managed by the operator: it is the
   * caller's responsibility to shut it down. It can be shared between event sources.
   *
   * @param executorService the executor to run the polls on
   * @return this builder for chained customization
   */
  @SuppressWarnings("unused")
  public PerResourcePollingConfigurationBuilder<R, P, ID> withExecutorService(
      ScheduledExecutorService executorService) {
    this.executorService = executorService;
    return this;
  }

  public PerResourcePollingConfigurationBuilder<R, P, ID> withRegisterPredicate(
      Predicate<P> registerPredicate) {
    this.registerPredicate = registerPredicate;
    return this;
  }

  public PerResourcePollingConfigurationBuilder<R, P, ID> withResourceIDMapper(
      ResourceIDMapper<R, ID> resourceIDMapper) {
    this.resourceIDMapper = resourceIDMapper;
    return this;
  }

  public PerResourcePollingConfigurationBuilder<R, P, ID> withName(String name) {
    this.name = name;
    return this;
  }

  public PerResourcePollingConfiguration<R, P, ID> build() {
    return new PerResourcePollingConfiguration<>(
        name,
        executorService,
        resourceIDMapper,
        resourceFetcher,
        registerPredicate,
        defaultPollingPeriod);
  }
}
