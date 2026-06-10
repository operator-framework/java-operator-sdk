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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.externalsecondaryupdate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

@ControllerConfiguration
public class ExternalSecondaryUpdateReconciler
    implements Reconciler<ExternalSecondaryUpdateCustomResource> {

  static final String CM_DATA_KEY = "managed-by";
  static final String CM_DATA_VALUE = "operator";
  static final String EXTERNAL_LABEL_KEY = "externally-set";
  static final String EXTERNAL_LABEL_VALUE = "yes";

  final AtomicInteger numberOfExecutions = new AtomicInteger();
  final CountDownLatch firstReconcileEntered = new CountDownLatch(1);
  final CountDownLatch externalUpdateApplied = new CountDownLatch(1);
  // Whether a later reconciliation (after the external label appeared) actually saw the label
  // through the informer/temp cache.
  final AtomicBoolean externalLabelSeenInLaterReconciliation = new AtomicBoolean();
  final AtomicReference<String> rvAfterCachingFilteringUpdate = new AtomicReference<>();

  private InformerEventSource<ConfigMap, ExternalSecondaryUpdateCustomResource>
      configMapEventSource;

  @Override
  public UpdateControl<ExternalSecondaryUpdateCustomResource> reconcile(
      ExternalSecondaryUpdateCustomResource resource,
      Context<ExternalSecondaryUpdateCustomResource> context)
      throws InterruptedException {
    int execution = numberOfExecutions.incrementAndGet();

    if (execution == 1) {
      // first reconciliation: create the secondary CM via SSA, then ask the test to apply
      // an external metadata change BEFORE we issue our second SSA on it.
      context.resourceOperations().serverSideApply(prepareCM(resource), configMapEventSource);

      firstReconcileEntered.countDown();
      if (!externalUpdateApplied.await(30, TimeUnit.SECONDS)) {
        throw new RuntimeException("timed out waiting for external CM update");
      }

      // second SSA on the secondary — the temp cache must filter out the event for OUR
      // resulting rv but NOT the rv from the external label change. We capture the rv our
      // SSA observed.
      var updated =
          context.resourceOperations().serverSideApply(prepareCM(resource), configMapEventSource);
      rvAfterCachingFilteringUpdate.set(updated.getMetadata().getResourceVersion());
    } else {
      // any subsequent reconciliation must be able to see the external label through the
      // informer cache (merged with the temp cache).
      var cached = context.getSecondaryResource(ConfigMap.class).orElse(null);
      if (cached != null
          && cached.getMetadata().getLabels() != null
          && EXTERNAL_LABEL_VALUE.equals(
              cached.getMetadata().getLabels().get(EXTERNAL_LABEL_KEY))) {
        externalLabelSeenInLaterReconciliation.set(true);
      }
    }
    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, ExternalSecondaryUpdateCustomResource>> prepareEventSources(
      EventSourceContext<ExternalSecondaryUpdateCustomResource> context) {
    configMapEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, ExternalSecondaryUpdateCustomResource.class)
                .build(),
            context);
    return List.of(configMapEventSource);
  }

  private static ConfigMap prepareCM(ExternalSecondaryUpdateCustomResource p) {
    var cm =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(p.getMetadata().getName())
                    .withNamespace(p.getMetadata().getNamespace())
                    .build())
            .withData(Map.of(CM_DATA_KEY, CM_DATA_VALUE))
            .build();
    cm.addOwnerReference(p);
    return cm;
  }
}
