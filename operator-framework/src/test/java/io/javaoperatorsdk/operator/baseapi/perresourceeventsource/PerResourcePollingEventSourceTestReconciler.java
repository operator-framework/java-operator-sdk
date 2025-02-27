package io.javaoperatorsdk.operator.baseapi.perresourceeventsource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingConfigurationBuilder;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;

@ControllerConfiguration
public class PerResourcePollingEventSourceTestReconciler
    implements Reconciler<PerResourceEventSourceCustomResource> {

  public static final int POLL_PERIOD = 100;
  private final Map<String, Integer> numberOfExecutions = new ConcurrentHashMap<>();
  private final Map<String, Integer> numberOfFetchExecutions = new ConcurrentHashMap<>();

  @Override
  public UpdateControl<PerResourceEventSourceCustomResource> reconcile(
      PerResourceEventSourceCustomResource resource,
      Context<PerResourceEventSourceCustomResource> context)
      throws Exception {
    numberOfExecutions.putIfAbsent(resource.getMetadata().getName(), 0);
    numberOfExecutions.compute(resource.getMetadata().getName(), (s, v) -> v + 1);
    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, PerResourceEventSourceCustomResource>> prepareEventSources(
      EventSourceContext<PerResourceEventSourceCustomResource> context) {
    PerResourcePollingEventSource<String, PerResourceEventSourceCustomResource> eventSource =
        new PerResourcePollingEventSource<>(
            String.class,
            context,
            new PerResourcePollingConfigurationBuilder<>(
                    (PerResourceEventSourceCustomResource resource) -> {
                      numberOfFetchExecutions.putIfAbsent(resource.getMetadata().getName(), 0);
                      numberOfFetchExecutions.compute(
                          resource.getMetadata().getName(), (s, v) -> v + 1);
                      return Set.of(UUID.randomUUID().toString());
                    },
                    Duration.ofMillis(POLL_PERIOD))
                .build());
    return List.of(eventSource);
  }

  public int getNumberOfExecutions(String name) {
    var num = numberOfExecutions.get(name);
    return num == null ? 0 : num;
  }

  public int getNumberOfFetchExecution(String name) {
    return numberOfFetchExecutions.get(name);
  }
}
