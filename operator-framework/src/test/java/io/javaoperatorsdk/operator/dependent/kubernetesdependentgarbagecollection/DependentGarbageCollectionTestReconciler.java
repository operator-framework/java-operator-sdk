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
package io.javaoperatorsdk.operator.dependent.kubernetesdependentgarbagecollection;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class DependentGarbageCollectionTestReconciler
    implements Reconciler<DependentGarbageCollectionTestCustomResource> {

  private KubernetesClient kubernetesClient;
  private volatile boolean errorOccurred = false;

  ConfigMapDependentResource configMapDependent;

  public DependentGarbageCollectionTestReconciler() {
    configMapDependent = new ConfigMapDependentResource();
  }

  @Override
  public List<EventSource<?, DependentGarbageCollectionTestCustomResource>> prepareEventSources(
      EventSourceContext<DependentGarbageCollectionTestCustomResource> context) {
    return EventSourceUtils.dependentEventSources(context, configMapDependent);
  }

  @Override
  public UpdateControl<DependentGarbageCollectionTestCustomResource> reconcile(
      DependentGarbageCollectionTestCustomResource primary,
      Context<DependentGarbageCollectionTestCustomResource> context) {

    if (primary.getSpec().isCreateConfigMap()) {
      configMapDependent.reconcile(primary, context);
    } else {
      configMapDependent.delete(primary, context);
    }

    return UpdateControl.noUpdate();
  }

  @Override
  public ErrorStatusUpdateControl<DependentGarbageCollectionTestCustomResource> updateErrorStatus(
      DependentGarbageCollectionTestCustomResource resource,
      Context<DependentGarbageCollectionTestCustomResource> context,
      Exception e) {
    // this can happen when a namespace is terminated in test
    if (e instanceof KubernetesClientException) {
      return ErrorStatusUpdateControl.noStatusUpdate();
    }
    errorOccurred = true;
    return ErrorStatusUpdateControl.noStatusUpdate();
  }

  public boolean isErrorOccurred() {
    return errorOccurred;
  }

  private static class ConfigMapDependentResource
      extends KubernetesDependentResource<ConfigMap, DependentGarbageCollectionTestCustomResource>
      implements Creator<ConfigMap, DependentGarbageCollectionTestCustomResource>,
          Updater<ConfigMap, DependentGarbageCollectionTestCustomResource>,
          GarbageCollected<DependentGarbageCollectionTestCustomResource> {

    @Override
    protected ConfigMap desired(
        DependentGarbageCollectionTestCustomResource primary,
        Context<DependentGarbageCollectionTestCustomResource> context) {
      ConfigMap configMap = new ConfigMap();
      configMap.setMetadata(
          new ObjectMetaBuilder()
              .withName(primary.getMetadata().getName())
              .withNamespace(primary.getMetadata().getNamespace())
              .build());
      configMap.setData(Map.of("key", "data"));
      return configMap;
    }
  }
}
