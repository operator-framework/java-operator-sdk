package io.javaoperatorsdk.operator.sample.multipledependentresource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class MultipleDependentResourceReconciler
    implements Reconciler<MultipleDependentResourceCustomResource>,
    TestExecutionInfoProvider, EventSourceInitializer<MultipleDependentResourceCustomResource>,
    KubernetesClientAware {

  public static final int FIRST_CONFIG_MAP_ID = 1;
  public static final int SECOND_CONFIG_MAP_ID = 2;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final MultipleDependentResourceConfigMap firstDependentResourceConfigMap;
  private final MultipleDependentResourceConfigMap secondDependentResourceConfigMap;
  private KubernetesClient client;

  public MultipleDependentResourceReconciler() {
    firstDependentResourceConfigMap = new MultipleDependentResourceConfigMap(FIRST_CONFIG_MAP_ID);

    secondDependentResourceConfigMap = new MultipleDependentResourceConfigMap(SECOND_CONFIG_MAP_ID);

    firstDependentResourceConfigMap
        .setResourceDiscriminator(
            new ResourceIDMatcherDiscriminator<>(
                p -> new ResourceID(p.getConfigMapName(FIRST_CONFIG_MAP_ID),
                    p.getMetadata().getNamespace())));
    secondDependentResourceConfigMap
        .setResourceDiscriminator(
            new ResourceIDMatcherDiscriminator<>(
                p -> new ResourceID(p.getConfigMapName(SECOND_CONFIG_MAP_ID),
                    p.getMetadata().getNamespace())));
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
    InformerEventSource<ConfigMap, MultipleDependentResourceCustomResource> eventSource =
        new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
            .build(), context);
    firstDependentResourceConfigMap.configureWith(eventSource);
    secondDependentResourceConfigMap.configureWith(eventSource);

    return EventSourceInitializer.nameEventSources(eventSource);
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
