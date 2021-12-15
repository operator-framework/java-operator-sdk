package io.javaoperatorsdk.operator.processing.event.source.timer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimerEventSourceTest {

  public static final int INITIAL_DELAY = 50;
  public static final int PERIOD = 50;

  private TimerEventSource<TestCustomResource> timerEventSource;
  private CapturingEventHandler eventHandler;

  @BeforeEach
  public void setup() {
    eventHandler = new CapturingEventHandler();

    timerEventSource = new TimerEventSource<>();
    EventSourceRegistry registryMock = mock(EventSourceRegistry.class);
    when(registryMock.getEventHandler()).thenReturn(eventHandler);

    timerEventSource.setEventRegistry(registryMock);
    timerEventSource.start();
  }

  @Test
  public void schedulesOnce() {
    TestCustomResource customResource = TestUtils.testCustomResource();

    timerEventSource.scheduleOnce(customResource, PERIOD);

    untilAsserted(() -> assertThat(eventHandler.events).hasSize(1));
    untilAsserted(PERIOD * 2, 0, () -> assertThat(eventHandler.events).hasSize(1));
  }

  @Test
  public void canCancelOnce() {
    TestCustomResource customResource = TestUtils.testCustomResource();

    timerEventSource.scheduleOnce(customResource, PERIOD);
    timerEventSource.cancelOnceSchedule(ResourceID.fromResource(customResource));

    untilAsserted(() -> assertThat(eventHandler.events).isEmpty());
  }

  @Test
  public void canRescheduleOnceEvent() {
    TestCustomResource customResource = TestUtils.testCustomResource();

    timerEventSource.scheduleOnce(customResource, PERIOD);
    timerEventSource.scheduleOnce(customResource, 2 * PERIOD);

    untilAsserted(PERIOD * 2, PERIOD, () -> assertThat(eventHandler.events).hasSize(1));
  }

  @Test
  public void deRegistersOnceEventSources() {
    TestCustomResource customResource = TestUtils.testCustomResource();

    timerEventSource.scheduleOnce(customResource, PERIOD);
    timerEventSource
        .onResourceDeleted(customResource);

    untilAsserted(() -> assertThat(eventHandler.events).isEmpty());
  }

  @Test
  public void eventNotRegisteredIfStopped() throws IOException {
    TestCustomResource customResource = TestUtils.testCustomResource();

    timerEventSource.stop();
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(
        () -> timerEventSource.scheduleOnce(customResource, PERIOD));
  }

  @Test
  public void eventNotFiredIfStopped() throws IOException {
    timerEventSource.scheduleOnce(TestUtils.testCustomResource(), PERIOD);
    timerEventSource.stop();

    untilAsserted(() -> assertThat(eventHandler.events).isEmpty());
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

  private static class CapturingEventHandler implements EventHandler {
    private final List<Event> events = new CopyOnWriteArrayList<>();

    @Override
    public void handleEvent(Event event) {
      events.add(event);
    }
  }
}
