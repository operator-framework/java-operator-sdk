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
package io.javaoperatorsdk.operator.health;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;

@SuppressWarnings("rawtypes")
public class ControllerHealthInfo {

  private static final Predicate<EventSource> UNHEALTHY = e -> e.getStatus() == Status.UNHEALTHY;
  private static final Predicate<EventSource> INFORMER =
      e -> e instanceof InformerWrappingEventSourceHealthIndicator;
  private static final Predicate<EventSource> UNHEALTHY_INFORMER =
      e -> INFORMER.test(e) && e.getStatus() == Status.UNHEALTHY;
  private static final Collector<EventSource, ?, Map<String, EventSourceHealthIndicator>>
      NAME_TO_ES_MAP = Collectors.toMap(EventSource::name, e -> e);
  private static final Collector<
          EventSource, ?, Map<String, InformerWrappingEventSourceHealthIndicator>>
      NAME_TO_ES_HEALTH_MAP =
          Collectors.toMap(EventSource::name, e -> (InformerWrappingEventSourceHealthIndicator) e);
  private final EventSourceManager<?> eventSourceManager;

  public ControllerHealthInfo(EventSourceManager eventSourceManager) {
    this.eventSourceManager = eventSourceManager;
  }

  public Map<String, EventSourceHealthIndicator> eventSourceHealthIndicators() {
    return eventSourceManager.allEventSourcesStream().collect(NAME_TO_ES_MAP);
  }

  /**
   * Whether the associated {@link io.javaoperatorsdk.operator.processing.Controller} has unhealthy
   * event sources.
   *
   * @return {@code true} if any of the associated controller is unhealthy, {@code false} otherwise
   * @since 5.3.0
   */
  public boolean hasUnhealthyEventSources() {
    return filteredEventSources(UNHEALTHY).findAny().isPresent();
  }

  public Map<String, EventSourceHealthIndicator> unhealthyEventSources() {
    return filteredEventSources(UNHEALTHY).collect(NAME_TO_ES_MAP);
  }

  private Stream<EventSource> filteredEventSources(Predicate<EventSource> filter) {
    return eventSourceManager.allEventSourcesStream().filter(filter);
  }

  public Map<String, InformerWrappingEventSourceHealthIndicator>
      informerEventSourceHealthIndicators() {
    return filteredEventSources(INFORMER).collect(NAME_TO_ES_HEALTH_MAP);
  }

  /**
   * @return Map with event sources that wraps an informer. Thus, either a {@link
   *     ControllerEventSource} or an {@link
   *     io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource}.
   */
  public Map<String, InformerWrappingEventSourceHealthIndicator>
      unhealthyInformerEventSourceHealthIndicators() {
    return filteredEventSources(UNHEALTHY_INFORMER).collect(NAME_TO_ES_HEALTH_MAP);
  }
}
