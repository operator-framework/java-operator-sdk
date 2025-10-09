/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
