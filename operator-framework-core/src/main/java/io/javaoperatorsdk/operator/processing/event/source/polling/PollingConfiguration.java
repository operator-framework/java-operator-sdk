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
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

import io.javaoperatorsdk.operator.processing.ResourceIDMapper;

/**
 * @param executorService the executor to run the polls on, {@code null} (the default) to run them
 *     on the executor the operator shares between all its scheduled tasks
 */
public record PollingConfiguration<R, ID>(
    String name,
    PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher,
    Duration period,
    ResourceIDMapper<R, ID> resourceIDMapper,
    ScheduledExecutorService executorService) {

  public PollingConfiguration(
      String name,
      PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher,
      Duration period,
      ResourceIDMapper<R, ID> resourceIDMapper) {
    this(name, genericResourceFetcher, period, resourceIDMapper, null);
  }

  public PollingConfiguration(
      String name,
      PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher,
      Duration period,
      ResourceIDMapper<R, ID> resourceIDMapper,
      ScheduledExecutorService executorService) {
    this.name = name;
    this.genericResourceFetcher = Objects.requireNonNull(genericResourceFetcher);
    this.period = period;
    this.resourceIDMapper =
        resourceIDMapper == null ? ResourceIDMapper.resourceIdProviderMapper() : resourceIDMapper;
    this.executorService = executorService;
  }
}
