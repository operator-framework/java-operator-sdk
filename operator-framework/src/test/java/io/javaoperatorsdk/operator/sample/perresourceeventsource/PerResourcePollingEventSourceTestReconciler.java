package io.javaoperatorsdk.operator.sample.perresourceeventsource;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;

@ControllerConfiguration
public class PerResourcePollingEventSourceTestReconciler
    implements Reconciler<PerResourceEventSourceCustomResource>,
    EventSourceInitializer<PerResourceEventSourceCustomResource>,
    KubernetesClientAware {

  public static final int POLL_PERIOD = 100;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final AtomicInteger numberOfFetchExecutions = new AtomicInteger(0);

  private KubernetesClient client;
  private PerResourcePollingEventSource<String, PerResourceEventSourceCustomResource> eventSource;

  @Override
  public UpdateControl<PerResourceEventSourceCustomResource> reconcile(
      PerResourceEventSourceCustomResource resource,
      Context<PerResourceEventSourceCustomResource> context) throws Exception {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<PerResourceEventSourceCustomResource> context) {
    this.eventSource =
        new PerResourcePollingEventSource<>(primaryResource -> {
          numberOfFetchExecutions.addAndGet(1);
          return Set.of(UUID.randomUUID().toString());
        },
            context.getPrimaryCache(), POLL_PERIOD, String.class);
    return EventSourceInitializer.nameEventSources(eventSource);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return client;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public int getNumberOfFetchExecution() {
    return numberOfFetchExecutions.get();
  }

}
