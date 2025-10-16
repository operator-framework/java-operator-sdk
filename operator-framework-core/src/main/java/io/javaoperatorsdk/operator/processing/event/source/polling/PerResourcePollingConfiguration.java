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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.ResourceKeyMapper;

public record PerResourcePollingConfiguration<R, P extends HasMetadata, ID>(
    String name,
    ScheduledExecutorService executorService,
    ResourceKeyMapper<R, ID> resourceKeyMapper,
    PerResourcePollingEventSource.ResourceFetcher<R, P> resourceFetcher,
    Predicate<P> registerPredicate,
    Duration defaultPollingPeriod) {

  public static final int DEFAULT_EXECUTOR_THREAD_NUMBER = 1;

  public PerResourcePollingConfiguration(
      String name,
      ScheduledExecutorService executorService,
      ResourceKeyMapper<R, ID> resourceKeyMapper,
      PerResourcePollingEventSource.ResourceFetcher<R, P> resourceFetcher,
      Predicate<P> registerPredicate,
      Duration defaultPollingPeriod) {
    this.name = name;
    this.executorService =
        executorService == null
            ? new ScheduledThreadPoolExecutor(DEFAULT_EXECUTOR_THREAD_NUMBER)
            : executorService;
    this.resourceKeyMapper =
        resourceKeyMapper == null
            ? ResourceKeyMapper.resourceIdProviderBasedMapper()
            : resourceKeyMapper;
    this.resourceFetcher = Objects.requireNonNull(resourceFetcher);
    this.registerPredicate = registerPredicate;
    this.defaultPollingPeriod = defaultPollingPeriod;
  }
}
