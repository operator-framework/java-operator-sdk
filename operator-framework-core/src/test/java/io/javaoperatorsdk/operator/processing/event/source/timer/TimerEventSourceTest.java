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
package io.javaoperatorsdk.operator.processing.event.source.timer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.reconciler.BaseControl;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSourceTestBase;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSourceTest.CapturingEventHandler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TimerEventSourceTest
    extends AbstractEventSourceTestBase<
        TimerEventSource<TestCustomResource>, CapturingEventHandler> {

  public static final int INITIAL_DELAY = 50;
  public static final int PERIOD = 50;

  @BeforeEach
  public void setup() {
    setUpSource(new TimerEventSource<>(), new CapturingEventHandler());
  }

  @Test
  public void schedulesOnce() {
    var resourceID = ResourceID.fromResource(TestUtils.testCustomResource());

    source.scheduleOnce(resourceID, PERIOD);

    untilAsserted(() -> assertThat(eventHandler.events).hasSize(1));
    untilAsserted(PERIOD * 2, 0, () -> assertThat(eventHandler.events).hasSize(1));
    assertThat(source.getStatus()).isEqualTo(Status.HEALTHY);
  }

  @Test
  public void canCancelOnce() {
    var resourceID = ResourceID.fromResource(TestUtils.testCustomResource());

    source.scheduleOnce(resourceID, PERIOD);
    source.cancelOnceSchedule(resourceID);

    untilAsserted(() -> assertThat(eventHandler.events).isEmpty());
    assertThat(source.getStatus()).isEqualTo(Status.HEALTHY);
  }

  @Test
  public void canRescheduleOnceEvent() {
    var resourceID = ResourceID.fromResource(TestUtils.testCustomResource());

    source.scheduleOnce(resourceID, PERIOD);
    source.scheduleOnce(resourceID, 2 * PERIOD);

    untilAsserted(PERIOD * 2, PERIOD, () -> assertThat(eventHandler.events).hasSize(1));
    assertThat(source.getStatus()).isEqualTo(Status.HEALTHY);
  }

  @Test
  public void deRegistersOnceEventSources() {
    TestCustomResource customResource = TestUtils.testCustomResource();

    source.scheduleOnce(ResourceID.fromResource(customResource), PERIOD);
    source.onResourceDeleted(customResource);

    untilAsserted(() -> assertThat(eventHandler.events).isEmpty());
    assertThat(source.getStatus()).isEqualTo(Status.HEALTHY);
  }

  @Test
  public void eventNotRegisteredIfStopped() {
    var resourceID = ResourceID.fromResource(TestUtils.testCustomResource());
    assertThat(source.getStatus()).isEqualTo(Status.HEALTHY);

    source.stop();
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> source.scheduleOnce(resourceID, PERIOD));
    assertThat(source.getStatus()).isEqualTo(Status.UNHEALTHY);
  }

  @Test
  public void eventNotFiredIfStopped() {
    source.scheduleOnce(ResourceID.fromResource(TestUtils.testCustomResource()), PERIOD);
    assertThat(source.getStatus()).isEqualTo(Status.HEALTHY);

    source.stop();

    untilAsserted(() -> assertThat(eventHandler.events).isEmpty());
    assertThat(source.getStatus()).isEqualTo(Status.UNHEALTHY);
  }

  @Test
  public void handlesInstanceReschedule() {
    var resourceID = ResourceID.fromResource(TestUtils.testCustomResource());

    source.scheduleOnce(resourceID, BaseControl.INSTANT_RESCHEDULE);

    assertThat(eventHandler.events).hasSize(1);
  }

  private void untilAsserted(ThrowingRunnable assertion) {
    untilAsserted(INITIAL_DELAY, PERIOD, assertion);
  }

  private void untilAsserted(long initialDelay, long interval, ThrowingRunnable assertion) {
    long delay = INITIAL_DELAY;
    long period = PERIOD;

    ConditionFactory cf = Awaitility.await();

    if (initialDelay > 0) {
      delay = initialDelay;
      cf = cf.pollDelay(initialDelay, TimeUnit.MILLISECONDS);
    }
    if (interval > 0) {
      period = interval;
      cf = cf.pollInterval(interval, TimeUnit.MILLISECONDS);
    }

    cf = cf.atMost(delay + (period * 3), TimeUnit.MILLISECONDS);
    cf.untilAsserted(assertion);
  }

  public static class CapturingEventHandler implements EventHandler {
    private final List<Event> events = new CopyOnWriteArrayList<>();

    @Override
    public void handleEvent(Event event) {
      events.add(event);
    }
  }
}
