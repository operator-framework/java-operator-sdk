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
package io.javaoperatorsdk.operator.baseapi.changenamespace;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class ChangeNamespaceTestReconciler
    implements Reconciler<ChangeNamespaceTestCustomResource> {

  private final ConcurrentHashMap<ResourceID, Integer> numberOfResourceReconciliations =
      new ConcurrentHashMap<>();

  @Override
  public List<EventSource<?, ChangeNamespaceTestCustomResource>> prepareEventSources(
      EventSourceContext<ChangeNamespaceTestCustomResource> context) {

    InformerEventSource<ConfigMap, ChangeNamespaceTestCustomResource> configMapES =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, ChangeNamespaceTestCustomResource.class)
                .build(),
            context);

    return List.of(configMapES);
  }

  @Override
  public UpdateControl<ChangeNamespaceTestCustomResource> reconcile(
      ChangeNamespaceTestCustomResource primary,
      Context<ChangeNamespaceTestCustomResource> context) {

    var actualConfigMap = context.getSecondaryResource(ConfigMap.class);
    if (actualConfigMap.isEmpty()) {
      context
          .getClient()
          .configMaps()
          .inNamespace(primary.getMetadata().getNamespace())
          .resource(configMap(primary))
          .create();
    }

    if (primary.getStatus() == null) {
      primary.setStatus(new ChangeNamespaceTestCustomResourceStatus());
    }
    increaseNumberOfResourceExecutions(primary);

    var statusPatchResource = new ChangeNamespaceTestCustomResource();
    statusPatchResource.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    statusPatchResource.setStatus(new ChangeNamespaceTestCustomResourceStatus());
    var statusUpdates = primary.getStatus().getNumberOfStatusUpdates();
    statusPatchResource.getStatus().setNumberOfStatusUpdates(statusUpdates + 1);
    return UpdateControl.patchStatus(statusPatchResource);
  }

  private void increaseNumberOfResourceExecutions(ChangeNamespaceTestCustomResource primary) {
    var resourceID = ResourceID.fromResource(primary);
    var num = numberOfResourceReconciliations.getOrDefault(resourceID, 0);
    numberOfResourceReconciliations.put(resourceID, num + 1);
  }

  public int numberOfResourceReconciliations(ChangeNamespaceTestCustomResource primary) {
    return numberOfResourceReconciliations.getOrDefault(ResourceID.fromResource(primary), 0);
  }

  private ConfigMap configMap(ChangeNamespaceTestCustomResource primary) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    configMap.setData(Map.of("data", primary.getMetadata().getName()));
    configMap.addOwnerReference(primary);
    return configMap;
  }
}
