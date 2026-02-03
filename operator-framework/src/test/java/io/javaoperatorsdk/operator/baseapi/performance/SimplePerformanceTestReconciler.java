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
package io.javaoperatorsdk.operator.baseapi.performance;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class SimplePerformanceTestReconciler
    implements Reconciler<SimplePerformanceTestResource>, Cleaner<SimplePerformanceTestResource> {

  public static final String KEY = "key";

  @Override
  public UpdateControl<SimplePerformanceTestResource> reconcile(
      SimplePerformanceTestResource resource, Context<SimplePerformanceTestResource> context) {
    var cm = configMap(resource);

    context.getClient().resource(cm).serverSideApply();

    resource.setStatus(new SimplePerformanceTestStatus());
    resource.getStatus().setValue(resource.getSpec().getValue());
    return UpdateControl.patchStatus(resource);
  }

  private ConfigMap configMap(SimplePerformanceTestResource primary) {
    var cm =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(primary.getMetadata().getName())
                    .withNamespace(primary.getMetadata().getNamespace())
                    .build())
            .withData(Map.of(KEY, primary.getSpec().getValue()))
            .build();
    cm.addOwnerReference(primary);
    return cm;
  }

  @Override
  public DeleteControl cleanup(
      SimplePerformanceTestResource resource, Context<SimplePerformanceTestResource> context) {
    return DeleteControl.defaultDelete();
  }

  @Override
  public List<EventSource<?, SimplePerformanceTestResource>> prepareEventSources(
      EventSourceContext<SimplePerformanceTestResource> context) {
    InformerEventSource<ConfigMap, SimplePerformanceTestResource> es =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, SimplePerformanceTestResource.class)
                .withNamespacesInheritedFromController()
                .build(),
            context);
    return List.of(es);
  }
}
