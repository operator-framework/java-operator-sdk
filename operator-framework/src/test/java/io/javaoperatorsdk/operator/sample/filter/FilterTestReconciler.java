package io.javaoperatorsdk.operator.sample.filter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration(onUpdateFilter = UpdateFilter.class)
public class FilterTestReconciler
    implements Reconciler<FilterTestCustomResource>,
    EventSourceInitializer<FilterTestCustomResource>,
    KubernetesClientAware {

  public static final String CONFIG_MAP_FILTER_VALUE = "config_map_skip_this";
  public static final String CUSTOM_RESOURCE_FILTER_VALUE = "custom_resource_skip_this";

  public static final String CM_VALUE_KEY = "value";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private KubernetesClient client;

  @Override
  public UpdateControl<FilterTestCustomResource> reconcile(
      FilterTestCustomResource resource,
      Context<FilterTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    client.configMaps().inNamespace(resource.getMetadata().getNamespace())
        .createOrReplace(createConfigMap(resource));
    return UpdateControl.noUpdate();
  }

  private ConfigMap createConfigMap(FilterTestCustomResource resource) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder()
        .withName(resource.getMetadata().getName())
        .withNamespace(resource.getMetadata().getNamespace())
        .build());
    configMap.addOwnerReference(resource);
    configMap.setData(Map.of(CM_VALUE_KEY, resource.getSpec().getValue()));
    return configMap;
  }


  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<FilterTestCustomResource> context) {

    InformerEventSource<ConfigMap, FilterTestCustomResource> configMapES =
        new InformerEventSource<>(InformerConfiguration
            .from(ConfigMap.class, context)
            .withOnUpdateFilter((newCM, oldCM) -> !newCM.getData().get(CM_VALUE_KEY)
                .equals(CONFIG_MAP_FILTER_VALUE))
            .build(), context);

    return EventSourceInitializer.nameEventSources(configMapES);
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return client;
  }
}
