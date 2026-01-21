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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.ReconcileUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class LatestDistinctTestReconciler
    implements Reconciler<LatestDistinctTestResource>, TestExecutionInfoProvider {

  public static final String EVENT_SOURCE_1_NAME = "configmap-es-1";
  public static final String EVENT_SOURCE_2_NAME = "configmap-es-2";
  public static final String LABEL_TYPE_1 = "type1";
  public static final String LABEL_TYPE_2 = "type2";
  public static final String LABEL_KEY = "configmap-type";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private volatile boolean errorOccurred = false;
  private final List<String> distinctConfigMapNames = new ArrayList<>();

  @Override
  public UpdateControl<LatestDistinctTestResource> reconcile(
      LatestDistinctTestResource resource, Context<LatestDistinctTestResource> context) {
    numberOfExecutions.incrementAndGet();

    // Get ConfigMaps from both event sources
    var eventSource1 =
        (InformerEventSource<ConfigMap, LatestDistinctTestResource>)
            context.eventSourceRetriever().getEventSourceFor(ConfigMap.class, EVENT_SOURCE_1_NAME);
    var eventSource2 =
        (InformerEventSource<ConfigMap, LatestDistinctTestResource>)
            context.eventSourceRetriever().getEventSourceFor(ConfigMap.class, EVENT_SOURCE_2_NAME);

    // Get all ConfigMaps from both event sources
    // Using list() with a predicate that always returns true to get all resources
    var configMapsFromEs1 = eventSource1.list(cm -> true);
    var configMapsFromEs2 = eventSource2.list(cm -> true);

    // Use latestDistinctList to deduplicate ConfigMaps by keeping the latest version
    List<ConfigMap> distinctConfigMaps =
        Stream.concat(configMapsFromEs1, configMapsFromEs2)
            .collect(ReconcileUtils.latestDistinctList());

    // Store the distinct ConfigMap names for verification
    synchronized (distinctConfigMapNames) {
      distinctConfigMapNames.clear();
      distinctConfigMapNames.addAll(
          distinctConfigMaps.stream()
              .map(cm -> cm.getMetadata().getName())
              .sorted()
              .collect(Collectors.toList()));
    }

    // Update status with information from ConfigMaps
    if (resource.getStatus() == null) {
      resource.setStatus(new LatestDistinctTestResourceStatus());
    }

    resource.getStatus().setConfigMapCount(distinctConfigMaps.size());

    // Concatenate data from all distinct ConfigMaps
    String data =
        distinctConfigMaps.stream()
            .map(cm -> cm.getData() != null ? cm.getData().getOrDefault("key", "") : "")
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(","));

    resource.getStatus().setDataFromConfigMaps(data);

    // Use ReconcileUtils to update the status
    // This tests serverSideApplyStatus method
    resource.getStatus().setReconcileUtilsCalled(true);
    return UpdateControl.patchStatus(ReconcileUtils.serverSideApplyStatus(context, resource));
  }

  @Override
  public List<EventSource<?, LatestDistinctTestResource>> prepareEventSources(
      EventSourceContext<LatestDistinctTestResource> context) {
    // Create two separate InformerEventSource instances for ConfigMaps
    // Each watches ConfigMaps with different labels

    // First event source: watches ConfigMaps with label "configmap-type: type1"
    var configEs1 =
        InformerEventSourceConfiguration.from(ConfigMap.class, LatestDistinctTestResource.class)
            .withName(EVENT_SOURCE_1_NAME)
            .withNamespacesInheritedFromController()
            .withLabelSelector(LABEL_KEY + "=" + LABEL_TYPE_1)
            .withSecondaryToPrimaryMapper(
                cm ->
                    Set.of(
                        new ResourceID(
                            cm.getMetadata().getOwnerReferences().get(0).getName(),
                            cm.getMetadata().getNamespace())))
            .build();

    // Second event source: watches ConfigMaps with label "configmap-type: type2"
    var configEs2 =
        InformerEventSourceConfiguration.from(ConfigMap.class, LatestDistinctTestResource.class)
            .withName(EVENT_SOURCE_2_NAME)
            .withNamespacesInheritedFromController()
            .withLabelSelector(LABEL_KEY + "=" + LABEL_TYPE_2)
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
  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public List<String> getDistinctConfigMapNames() {
    synchronized (distinctConfigMapNames) {
      return new ArrayList<>(distinctConfigMapNames);
    }
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
}
