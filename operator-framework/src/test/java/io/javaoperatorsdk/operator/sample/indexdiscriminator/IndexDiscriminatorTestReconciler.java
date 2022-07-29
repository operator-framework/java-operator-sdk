package io.javaoperatorsdk.operator.sample.indexdiscriminator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class IndexDiscriminatorTestReconciler
    implements Reconciler<IndexDiscriminatorTestCustomResource>,
    Cleaner<IndexDiscriminatorTestCustomResource>,
    TestExecutionInfoProvider, EventSourceInitializer<IndexDiscriminatorTestCustomResource>,
    KubernetesClientAware {

  public static final String FIRST_CONFIG_MAP_SUFFIX_1 = "-1";
  public static final String FIRST_CONFIG_MAP_SUFFIX_2 = "-2";
  public static final String CONFIG_MAP_INDEX_1 = "CONFIG_MAP_INDEX1";
  public static final String CONFIG_MAP_INDEX_2 = "CONFIG_MAP_INDEX2";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final IndexDiscriminatorTestDRConfigMap firstDependentResourceConfigMap;
  private final IndexDiscriminatorTestDRConfigMap secondDependentResourceConfigMap;
  private KubernetesClient client;

  public IndexDiscriminatorTestReconciler() {
    firstDependentResourceConfigMap =
        new IndexDiscriminatorTestDRConfigMap(FIRST_CONFIG_MAP_SUFFIX_1);
    secondDependentResourceConfigMap =
        new IndexDiscriminatorTestDRConfigMap(FIRST_CONFIG_MAP_SUFFIX_2);
  }

  @Override
  public UpdateControl<IndexDiscriminatorTestCustomResource> reconcile(
      IndexDiscriminatorTestCustomResource resource,
      Context<IndexDiscriminatorTestCustomResource> context) {
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
      EventSourceContext<IndexDiscriminatorTestCustomResource> context) {

    InformerEventSource<ConfigMap, IndexDiscriminatorTestCustomResource> eventSource =
        new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
            .build(), context);

    eventSource.addIndexer(CONFIG_MAP_INDEX_1, cm -> {
      if (cm.getMetadata().getName().endsWith(FIRST_CONFIG_MAP_SUFFIX_1)) {
        return List.of(configMapKey(cm));
      } else {
        return Collections.emptyList();
      }
    });
    eventSource.addIndexer(CONFIG_MAP_INDEX_2, cm -> {
      if (cm.getMetadata().getName().endsWith(FIRST_CONFIG_MAP_SUFFIX_2)) {
        return List.of(configMapKey(cm));
      } else {
        return Collections.emptyList();
      }
    });

    firstDependentResourceConfigMap.configureWith(eventSource);
    secondDependentResourceConfigMap.configureWith(eventSource);

    firstDependentResourceConfigMap
        .setResourceDiscriminator(
            new IndexDiscriminator(CONFIG_MAP_INDEX_1, FIRST_CONFIG_MAP_SUFFIX_1));
    secondDependentResourceConfigMap
        .setResourceDiscriminator(
            new IndexDiscriminator(CONFIG_MAP_INDEX_2, FIRST_CONFIG_MAP_SUFFIX_2));
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

  public static String configMapKey(ConfigMap configMap) {
    return configMap.getMetadata().getName() + "#" + configMap.getMetadata().getNamespace();
  }

  public static String configMapKeyFromPrimary(IndexDiscriminatorTestCustomResource primary,
      String nameSuffix) {
    return primary.getMetadata().getName() + nameSuffix + "#"
        + primary.getMetadata().getNamespace();
  }

  @Override
  public DeleteControl cleanup(IndexDiscriminatorTestCustomResource resource,
      Context<IndexDiscriminatorTestCustomResource> context) {
    firstDependentResourceConfigMap.delete(resource, context);
    secondDependentResourceConfigMap.delete(resource, context);
    return DeleteControl.defaultDelete();
  }
}
