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
package io.javaoperatorsdk.operator.processing.event;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class EventProcessorThroughputTest {

  private static final Logger log = LoggerFactory.getLogger(EventProcessorThroughputTest.class);
  private static final int EVENT_COUNT = 1000;

  private EventProcessor eventProcessor;

  @BeforeEach
  void setup() {
    ReconciliationDispatcher reconciliationDispatcher = mock(ReconciliationDispatcher.class);
    when(reconciliationDispatcher.handleExecution(any()))
        .thenReturn(PostExecutionControl.defaultDispatch());

    EventSourceManager eventSourceManager = mock(EventSourceManager.class);
    ControllerEventSource controllerEventSource = mock(ControllerEventSource.class);
    when(eventSourceManager.getControllerEventSource()).thenReturn(controllerEventSource);

    TimerEventSource retryTimerEventSource = mock(TimerEventSource.class);

    ControllerConfiguration config = mock(ControllerConfiguration.class);
    when(config.getName()).thenReturn("throughput-test");
    when(config.getRetry()).thenReturn(null);
    when(config.getRateLimiter()).thenReturn(LinearRateLimiter.deactivatedRateLimiter());
    when(config.maxReconciliationInterval()).thenReturn(Optional.of(Duration.ofHours(1)));
    when(config.getConfigurationService()).thenReturn(new BaseConfigurationService());
    when(config.triggerReconcilerOnAllEvents()).thenReturn(false);

    eventProcessor =
        spy(new EventProcessor(config, reconciliationDispatcher, eventSourceManager, null));
    when(eventProcessor.retryEventSource()).thenReturn(retryTimerEventSource);
    eventProcessor.start();
  }

  @AfterEach
  void tearDown() {
    eventProcessor.stop();
  }

  @Test
  void shouldProcessEventsWithAcceptableThroughput() {
    long startTime = System.nanoTime();

    for (int i = 0; i < EVENT_COUNT; i++) {
      var cr = TestUtils.testCustomResource();
      ResourceID resourceID = ResourceID.fromResource(cr);
      ResourceEvent event = new ResourceEvent(ResourceAction.UPDATED, resourceID, cr);
      eventProcessor.handleEvent(event);
    }

    long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
    double eventsPerSecond = EVENT_COUNT * 1000.0 / elapsedMs;

    log.info(
        "EventProcessor throughput: {} events in {} ms ({} events/sec)",
        EVENT_COUNT,
        elapsedMs,
        String.format("%.1f", eventsPerSecond));

    assertThat(eventsPerSecond)
        .as("Event processing throughput should exceed 100 events/sec")
        .isGreaterThan(100.0);
  }
}
