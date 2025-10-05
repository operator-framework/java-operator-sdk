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
package io.javaoperatorsdk.operator.baseapi.createupdateeventfilter;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class CreateUpdateEventFilterTestReconciler
    implements Reconciler<CreateUpdateEventFilterTestCustomResource> {

  public static final String CONFIG_MAP_TEST_DATA_KEY = "key";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final DirectConfigMapDependentResource configMapDR =
      new DirectConfigMapDependentResource(ConfigMap.class);

  @Override
  public UpdateControl<CreateUpdateEventFilterTestCustomResource> reconcile(
      CreateUpdateEventFilterTestCustomResource resource,
      Context<CreateUpdateEventFilterTestCustomResource> context) {
    numberOfExecutions.incrementAndGet();

    ConfigMap configMap =
        context
            .getClient()
            .configMaps()
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName())
            .get();
    if (configMap == null) {
      configMapDR.desired = createConfigMap(resource);
      configMapDR.reconcile(resource, context);
    } else {
      if (!Objects.equals(
          configMap.getData().get(CONFIG_MAP_TEST_DATA_KEY), resource.getSpec().getValue())) {
        configMap.getData().put(CONFIG_MAP_TEST_DATA_KEY, resource.getSpec().getValue());
        configMapDR.desired = configMap;
        configMapDR.reconcile(resource, context);
      }
    }
    return UpdateControl.noUpdate();
  }

  private ConfigMap createConfigMap(CreateUpdateEventFilterTestCustomResource resource) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(resource.getMetadata().getName());
    configMap.getMetadata().setLabels(new HashMap<>());
    configMap.getMetadata().getLabels().put("integrationtest", this.getClass().getSimpleName());
    configMap.getMetadata().setNamespace(resource.getMetadata().getNamespace());
    configMap.setData(new HashMap<>());
    configMap.getData().put(CONFIG_MAP_TEST_DATA_KEY, resource.getSpec().getValue());
    configMap.addOwnerReference(resource);

    return configMap;
  }

  @Override
  public List<EventSource<?, CreateUpdateEventFilterTestCustomResource>> prepareEventSources(
      EventSourceContext<CreateUpdateEventFilterTestCustomResource> context) {
    InformerEventSourceConfiguration<ConfigMap> informerConfiguration =
        InformerEventSourceConfiguration.from(
                ConfigMap.class, CreateUpdateEventFilterTestCustomResource.class)
            .withLabelSelector("integrationtest = " + this.getClass().getSimpleName())
            .build();

    final var informerEventSource = new InformerEventSource<>(informerConfiguration, context);
    this.configMapDR.setEventSource(informerEventSource);

    return List.of(informerEventSource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  private static final class DirectConfigMapDependentResource
      extends CRUDKubernetesDependentResource<
          ConfigMap, CreateUpdateEventFilterTestCustomResource> {

    private ConfigMap desired;

    private DirectConfigMapDependentResource(Class<ConfigMap> resourceType) {
      super(resourceType);
    }

    @Override
    protected ConfigMap desired(
        CreateUpdateEventFilterTestCustomResource primary,
        Context<CreateUpdateEventFilterTestCustomResource> context) {
      return desired;
    }

    @Override
    public void setEventSource(
        io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource<
                ConfigMap, CreateUpdateEventFilterTestCustomResource>
            eventSource) {
      super.setEventSource(eventSource);
    }
  }
}
