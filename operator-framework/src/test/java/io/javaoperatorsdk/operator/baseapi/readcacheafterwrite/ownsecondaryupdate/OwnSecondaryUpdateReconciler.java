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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.ownsecondaryupdate;

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

@ControllerConfiguration(generationAwareEventProcessing = false)
public class OwnSecondaryUpdateReconciler implements Reconciler<OwnSecondaryUpdateCustomResource> {

  static final int OWN_SSA_COUNT = 3;

  final AtomicInteger numberOfExecutions = new AtomicInteger();

  private InformerEventSource<ConfigMap, OwnSecondaryUpdateCustomResource> configMapEventSource;

  @Override
  public UpdateControl<OwnSecondaryUpdateCustomResource> reconcile(
      OwnSecondaryUpdateCustomResource resource,
      Context<OwnSecondaryUpdateCustomResource> context) {
    numberOfExecutions.incrementAndGet();

    // Issue several SSA writes on the secondary, each with distinct data so the resource
    // version actually advances. With the read-cache-after-write filter in place, none of the
    // resulting watch events should trigger a fresh reconciliation.
    for (int i = 1; i <= OWN_SSA_COUNT; i++) {
      context.resourceOperations().serverSideApply(prepareCM(resource, i), configMapEventSource);
    }
    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, OwnSecondaryUpdateCustomResource>> prepareEventSources(
      EventSourceContext<OwnSecondaryUpdateCustomResource> context) {
    configMapEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, OwnSecondaryUpdateCustomResource.class)
                .build(),
            context);
    return List.of(configMapEventSource);
  }

  private static ConfigMap prepareCM(OwnSecondaryUpdateCustomResource p, int iteration) {
    var cm =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(p.getMetadata().getName())
                    .withNamespace(p.getMetadata().getNamespace())
                    .build())
            .withData(Map.of("iteration", "" + iteration))
            .build();
    cm.addOwnerReference(p);
    return cm;
  }
}
