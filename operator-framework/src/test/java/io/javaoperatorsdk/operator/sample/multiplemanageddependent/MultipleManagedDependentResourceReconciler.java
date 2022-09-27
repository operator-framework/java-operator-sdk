package io.javaoperatorsdk.operator.sample.multiplemanageddependent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class MultipleManagedDependentResourceReconciler
    implements Reconciler<MultipleManagedDependentResourceCustomResource>,
    TestExecutionInfoProvider,
    EventSourceInitializer<MultipleManagedDependentResourceCustomResource> {

  public static final int FIRST_CONFIG_MAP_ID = 1;
  public static final int SECOND_CONFIG_MAP_ID = 2;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private KubernetesClient client;

  public MultipleManagedDependentResourceReconciler() {}

  @Override
  public UpdateControl<MultipleManagedDependentResourceCustomResource> reconcile(
      MultipleManagedDependentResourceCustomResource resource,
      Context<MultipleManagedDependentResourceCustomResource> context) {
    numberOfExecutions.getAndIncrement();

    return UpdateControl.noUpdate();
  }


  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<MultipleManagedDependentResourceCustomResource> context) {
    return null;
  }
}
