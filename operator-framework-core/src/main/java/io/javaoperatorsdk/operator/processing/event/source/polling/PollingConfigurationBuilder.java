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

import io.javaoperatorsdk.operator.processing.ResourceIDMapper;

public final class PollingConfigurationBuilder<R, ID> {
  private final Duration period;
  private final PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher;
  private ResourceIDMapper<R, ID> resourceIDMapper;
  private String name;
  private ScheduledExecutorService executorService;

  public PollingConfigurationBuilder(
      PollingEventSource.GenericResourceFetcher<R> fetcher, Duration period) {
    this.genericResourceFetcher = fetcher;
    this.period = period;
  }

  public PollingConfigurationBuilder<R, ID> withResourceIDMapper(
      ResourceIDMapper<R, ID> resourceIDMapper) {
    this.resourceIDMapper = resourceIDMapper;
    return this;
  }

  public PollingConfigurationBuilder<R, ID> withName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Runs the polls on the specified executor instead of the one the operator shares between all its
   * scheduled tasks. Note that an explicitly provided executor is not managed by the operator: it
   * is the caller's responsibility to shut it down.
   *
   * @param executorService the executor to run the polls on
   * @return this builder for chained customization
   * @since 5.6.0
   */
  public PollingConfigurationBuilder<R, ID> withExecutorService(
      ScheduledExecutorService executorService) {
    this.executorService = executorService;
    return this;
  }

  public PollingConfiguration<R, ID> build() {
    return new PollingConfiguration<>(
        name, genericResourceFetcher, period, resourceIDMapper, executorService);
  }
}
