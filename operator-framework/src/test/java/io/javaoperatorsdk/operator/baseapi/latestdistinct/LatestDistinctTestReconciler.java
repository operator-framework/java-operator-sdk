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
package io.javaoperatorsdk.operator.baseapi.latestdistinct;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class LatestDistinctTestReconciler implements Reconciler<LatestDistinctTestResource> {

  public static final String EVENT_SOURCE_1_NAME = "configmap-es-1";
  public static final String EVENT_SOURCE_2_NAME = "configmap-es-2";
  public static final String LABEL_KEY = "configmap-type";
  public static final String KEY_2 = "key2";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private volatile boolean errorOccurred = false;

  @Override
  public UpdateControl<LatestDistinctTestResource> reconcile(
      LatestDistinctTestResource resource, Context<LatestDistinctTestResource> context) {

    // Update status with information from ConfigMaps
    if (resource.getStatus() == null) {
      resource.setStatus(new LatestDistinctTestResourceStatus());
    }
    var allConfigMaps = context.getSecondaryResourcesAsStream(ConfigMap.class).toList();
    if (allConfigMaps.size() < 2) {
      // wait until both informers see the config map
      return UpdateControl.noUpdate();
    }
    // makes sure that distinc config maps returned
    var distinctConfigMaps = context.getSecondaryResourcesAsStream(ConfigMap.class, true).toList();
    if (distinctConfigMaps.size() != 1) {
      errorOccurred = true;
      throw new IllegalStateException();
    }

    resource.getStatus().setConfigMapCount(distinctConfigMaps.size());
    var configMap = distinctConfigMaps.get(0);
    configMap.setData(Map.of(KEY_2, "val2"));
    var updated = context.resourceOperations().update(configMap);

    // makes sure that distinct config maps returned
    distinctConfigMaps = context.getSecondaryResourcesAsStream(ConfigMap.class, true).toList();
    if (distinctConfigMaps.size() != 1) {
      errorOccurred = true;
      throw new IllegalStateException();
    }
    configMap = distinctConfigMaps.get(0);
    if (!configMap.getData().containsKey(KEY_2)
        || !configMap
            .getMetadata()
            .getResourceVersion()
            .equals(updated.getMetadata().getResourceVersion())) {
      errorOccurred = true;
      throw new IllegalStateException();
    }
    numberOfExecutions.incrementAndGet();
    return UpdateControl.patchStatus(resource);
  }

  @Override
  public List<EventSource<?, LatestDistinctTestResource>> prepareEventSources(
      EventSourceContext<LatestDistinctTestResource> context) {
    var configEs1 =
        InformerEventSourceConfiguration.from(ConfigMap.class, LatestDistinctTestResource.class)
            .withName(EVENT_SOURCE_1_NAME)
            .withLabelSelector(LABEL_KEY)
            .withNamespacesInheritedFromController()
            .withSecondaryToPrimaryMapper(
                cm ->
                    Set.of(
                        new ResourceID(
                            cm.getMetadata().getOwnerReferences().get(0).getName(),
                            cm.getMetadata().getNamespace())))
            .build();

    var configEs2 =
        InformerEventSourceConfiguration.from(ConfigMap.class, LatestDistinctTestResource.class)
            .withName(EVENT_SOURCE_2_NAME)
            .withLabelSelector(LABEL_KEY)
            .withNamespacesInheritedFromController()
            .withSecondaryToPrimaryMapper(
                cm ->
                    Set.of(
                        new ResourceID(
                            cm.getMetadata().getOwnerReferences().get(0).getName(),
                            cm.getMetadata().getNamespace())))
            .build();

    return List.of(
        new InformerEventSource<>(configEs1, context),
        new InformerEventSource<>(configEs2, context));
  }

  @Override
  public ErrorStatusUpdateControl<LatestDistinctTestResource> updateErrorStatus(
      LatestDistinctTestResource resource,
      Context<LatestDistinctTestResource> context,
      Exception e) {
    errorOccurred = true;
    return ErrorStatusUpdateControl.noStatusUpdate();
  }

  public boolean isErrorOccurred() {
    return errorOccurred;
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
