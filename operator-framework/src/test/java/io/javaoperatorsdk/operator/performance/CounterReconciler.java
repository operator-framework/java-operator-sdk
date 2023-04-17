package io.javaoperatorsdk.operator.performance;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration()
class CounterReconciler implements Reconciler<Counter>, EventSourceInitializer<Counter> {

  private final AtomicInteger eventCounter;
  private final EventSource eventSource;

  private final Logger log = LoggerFactory.getLogger(getClass());

  public CounterReconciler(AtomicInteger eventCounter, EventSource eventSource) {
    this.eventCounter = eventCounter;
    this.eventSource = eventSource;
  }

  @Override
  public UpdateControl<Counter> reconcile(Counter counter, Context<Counter> context)
      throws Exception {
    int eventCount = eventCounter.incrementAndGet();
    if (eventCount % 1 == 0) {
      log.info("Reconciling event: {}", eventCount);
    }

    if (counter.getStatus() == null) {
      counter.setStatus(new CounterStatus());
    }
    counter.getStatus().setCount(counter.getSpec().getCount());
    return UpdateControl.noUpdate();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Counter> context) {
    return EventSourceInitializer.nameEventSources(eventSource);
  }
}
