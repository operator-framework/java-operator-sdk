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

import io.javaoperatorsdk.operator.processing.ResourceIDMapper;

public final class PollingConfigurationBuilder<R, ID> {
  private final Duration period;
  private final PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher;
  private ResourceIDMapper<R, ID> resourceIDMapper;
  private String name;

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

  public PollingConfiguration<R, ID> build() {
    return new PollingConfiguration<>(name, genericResourceFetcher, period, resourceIDMapper);
  }
}
