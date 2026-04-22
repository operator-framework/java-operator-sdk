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
package io.javaoperatorsdk.operator.baseapi.ownerreferencemultiversion;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

@ControllerConfiguration
public class OwnerRefMultiVersionReconciler implements Reconciler<OwnerRefMultiVersionCR1> {

  public static final String LABEL_KEY = "ownerref-multiversion-test";
  public static final String LABEL_VALUE = "true";
  public static final String DATA_KEY = "data";

  private final AtomicInteger reconcileCount = new AtomicInteger(0);

  @Override
  public UpdateControl<OwnerRefMultiVersionCR1> reconcile(
      OwnerRefMultiVersionCR1 resource, Context<OwnerRefMultiVersionCR1> context) {

    var client = context.getClient();
    var configMapName = resource.getMetadata().getName();
    var namespace = resource.getMetadata().getNamespace();

    var existingCM = client.configMaps().inNamespace(namespace).withName(configMapName).get();
    if (existingCM == null) {
      var cm =
          new ConfigMapBuilder()
              .withMetadata(
                  new ObjectMetaBuilder()
                      .withName(configMapName)
                      .withNamespace(namespace)
                      .withLabels(Map.of(LABEL_KEY, LABEL_VALUE))
                      .build())
              .withData(Map.of(DATA_KEY, resource.getSpec().getValue()))
              .build();
      cm.addOwnerReference(resource);
      client.configMaps().resource(cm).create();
    }

    int count = reconcileCount.incrementAndGet();
    if (resource.getStatus() == null) {
      resource.setStatus(new OwnerRefMultiVersionStatus());
    }
    resource.getStatus().setReconcileCount(count);
    return UpdateControl.patchStatus(resource);
  }

  @Override
  public List<EventSource<?, OwnerRefMultiVersionCR1>> prepareEventSources(
      EventSourceContext<OwnerRefMultiVersionCR1> context) {
    var ies =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(ConfigMap.class, OwnerRefMultiVersionCR1.class)
                .withSecondaryToPrimaryMapper(
                    Mappers.fromOwnerReferences(context.getPrimaryResourceClass()))
                .withLabelSelector(LABEL_KEY + "=" + LABEL_VALUE)
                .build(),
            context);
    return List.of(ies);
  }

  public int getReconcileCount() {
    return reconcileCount.get();
  }
}
