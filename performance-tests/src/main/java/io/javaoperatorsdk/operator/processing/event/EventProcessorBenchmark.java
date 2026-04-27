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
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
@SuppressWarnings({"rawtypes", "unchecked"})
public class EventProcessorBenchmark {

  private EventProcessor eventProcessor;

  @Setup(Level.Iteration)
  public void setup() {
    ReconciliationDispatcher reconciliationDispatcher = mock(ReconciliationDispatcher.class);
    when(reconciliationDispatcher.handleExecution(any()))
        .thenReturn(PostExecutionControl.defaultDispatch());

    EventSourceManager eventSourceManager = mock(EventSourceManager.class);
    ControllerEventSource controllerEventSource = mock(ControllerEventSource.class);
    when(eventSourceManager.getControllerEventSource()).thenReturn(controllerEventSource);

    TimerEventSource retryTimerEventSource = mock(TimerEventSource.class);

    ControllerConfiguration config = mock(ControllerConfiguration.class);
    when(config.getName()).thenReturn("benchmark");
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

  @TearDown(Level.Iteration)
  public void tearDown() {
    eventProcessor.stop();
  }

  @Benchmark
  public void handleSingleEvent(Blackhole bh) {
    var cr = TestUtils.testCustomResource();
    ResourceID resourceID = ResourceID.fromResource(cr);
    ResourceEvent event = new ResourceEvent(ResourceAction.UPDATED, resourceID, cr);
    eventProcessor.handleEvent(event);
  }
}
