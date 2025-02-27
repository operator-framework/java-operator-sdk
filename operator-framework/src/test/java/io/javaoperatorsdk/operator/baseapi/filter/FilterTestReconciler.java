package io.javaoperatorsdk.operator.baseapi.filter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration(informer = @Informer(onUpdateFilter = UpdateFilter.class))
public class FilterTestReconciler implements Reconciler<FilterTestCustomResource> {

  public static final String CONFIG_MAP_FILTER_VALUE = "config_map_skip_this";
  public static final String CUSTOM_RESOURCE_FILTER_VALUE = "custom_resource_skip_this";

  public static final String CM_VALUE_KEY = "value";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<FilterTestCustomResource> reconcile(
      FilterTestCustomResource resource, Context<FilterTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    context
        .getClient()
        .configMaps()
        .inNamespace(resource.getMetadata().getNamespace())
        .resource(createConfigMap(resource))
        .createOrReplace();
    return UpdateControl.noUpdate();
  }

  private ConfigMap createConfigMap(FilterTestCustomResource resource) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder()
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
  public List<EventSource<?, FilterTestCustomResource>> prepareEventSources(
      EventSourceContext<FilterTestCustomResource> context) {

    final var informerConfiguration =
        InformerEventSourceConfiguration.from(ConfigMap.class, FilterTestCustomResource.class)
            .withOnUpdateFilter(
                (newCM, oldCM) ->
                    !newCM.getData().get(CM_VALUE_KEY).equals(CONFIG_MAP_FILTER_VALUE))
            .build();
    InformerEventSource<ConfigMap, FilterTestCustomResource> configMapES =
        new InformerEventSource<>(informerConfiguration, context);

    return List.of(configMapES);
  }
}
