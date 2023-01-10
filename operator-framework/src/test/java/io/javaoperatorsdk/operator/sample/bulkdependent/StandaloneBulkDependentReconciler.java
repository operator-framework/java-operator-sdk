package io.javaoperatorsdk.operator.sample.bulkdependent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class StandaloneBulkDependentReconciler
    implements Reconciler<BulkDependentTestCustomResource>, TestExecutionInfoProvider,
    EventSourceInitializer<BulkDependentTestCustomResource>, KubernetesClientAware {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final ConfigMapDeleterBulkDependentResource dependent;
  private KubernetesClient kubernetesClient;

  public StandaloneBulkDependentReconciler() {
    dependent = new CRUDConfigMapBulkDependentResource();
  }

  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource,
      Context<BulkDependentTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    dependent.reconcile(resource, context);

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<BulkDependentTestCustomResource> context) {
    return EventSourceInitializer
        .nameEventSources(dependent.initEventSource(context));
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
    dependent.setKubernetesClient(kubernetesClient);
  }
}
