package io.javaoperatorsdk.operator.sample.multipledependentresource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class MultipleDependentResourceReconciler
    implements Reconciler<MultipleDependentResourceCustomResource>,
    TestExecutionInfoProvider, EventSourceInitializer<MultipleDependentResourceCustomResource>,
    KubernetesClientAware {

  public static final int FIRST_CONFIG_MAP_ID = 1;
  public static final int SECOND_CONFIG_MAP_ID = 2;
  public static final String LABEL = "multipledependentresource";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final MultipleDependentResourceConfigMap firstDependentResourceConfigMap;
  private final MultipleDependentResourceConfigMap secondDependentResourceConfigMap;
  private KubernetesClient client;

  public MultipleDependentResourceReconciler() {
    firstDependentResourceConfigMap = new MultipleDependentResourceConfigMap(FIRST_CONFIG_MAP_ID);
    secondDependentResourceConfigMap = new MultipleDependentResourceConfigMap(SECOND_CONFIG_MAP_ID);

    firstDependentResourceConfigMap.configureWith(
        new KubernetesDependentResourceConfig()
            .setLabelSelector(getLabelSelector(FIRST_CONFIG_MAP_ID)));
    secondDependentResourceConfigMap.configureWith(
        new KubernetesDependentResourceConfig()
            .setLabelSelector(getLabelSelector(SECOND_CONFIG_MAP_ID)));
  }

  private String getLabelSelector(int resourceId) {
    return LABEL + "=" + resourceId;
  }

  @Override
  public UpdateControl<MultipleDependentResourceCustomResource> reconcile(
      MultipleDependentResourceCustomResource resource,
      Context<MultipleDependentResourceCustomResource> context) {
    numberOfExecutions.getAndIncrement();
    firstDependentResourceConfigMap.reconcile(resource, context);
    secondDependentResourceConfigMap.reconcile(resource, context);
    return UpdateControl.noUpdate();
  }


  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<MultipleDependentResourceCustomResource> context) {
    return EventSourceInitializer.nameEventSources(
        firstDependentResourceConfigMap.initEventSource(context),
        secondDependentResourceConfigMap.initEventSource(context));
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return client;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
    firstDependentResourceConfigMap.setKubernetesClient(kubernetesClient);
    secondDependentResourceConfigMap.setKubernetesClient(kubernetesClient);
  }
}
