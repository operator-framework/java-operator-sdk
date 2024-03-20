package io.javaoperatorsdk.operator.sample.perresourceeventsource;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ControllerConfiguration
public class PerResourcePollingEventSourceTestReconciler
    implements Reconciler<PerResourceEventSourceCustomResource> {

  public static final int POLL_PERIOD = 100;
  private final Map<String, Integer> numberOfExecutions = new ConcurrentHashMap<>();
  private final Map<String, Integer> numberOfFetchExecutions = new ConcurrentHashMap<>();

  @Override
  public UpdateControl<PerResourceEventSourceCustomResource> reconcile(
      PerResourceEventSourceCustomResource resource,
      Context<PerResourceEventSourceCustomResource> context) throws Exception {
    numberOfExecutions.putIfAbsent(resource.getMetadata().getName(), 0);
    numberOfExecutions.compute(resource.getMetadata().getName(), (s, v) -> v + 1);
    return UpdateControl.noUpdate();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<PerResourceEventSourceCustomResource> context) {
    PerResourcePollingEventSource<String, PerResourceEventSourceCustomResource> eventSource =
        new PerResourcePollingEventSource<>(resource -> {
          numberOfFetchExecutions.putIfAbsent(resource.getMetadata().getName(), 0);
          numberOfFetchExecutions.compute(resource.getMetadata().getName(), (s, v) -> v + 1);
          return Set.of(UUID.randomUUID().toString());
        },
            context, Duration.ofMillis(POLL_PERIOD), String.class);
    return EventSourceUtils.nameEventSources(eventSource);
  }

  public int getNumberOfExecutions(String name) {
    var num = numberOfExecutions.get(name);
    return num == null ? 0 : num;
  }

  public int getNumberOfFetchExecution(String name) {
    return numberOfFetchExecutions.get(name);
  }

}
