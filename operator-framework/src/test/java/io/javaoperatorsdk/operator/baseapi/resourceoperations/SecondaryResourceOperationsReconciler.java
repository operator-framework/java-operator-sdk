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
package io.javaoperatorsdk.operator.baseapi.resourceoperations;

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
import io.javaoperatorsdk.operator.api.reconciler.ResourceOperations.Options;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

/**
 * Exercises the secondary-resource variants of {@link
 * io.javaoperatorsdk.operator.api.reconciler.ResourceOperations} - the overloads that take an
 * {@link InformerEventSource} so the written secondary is cached (and its own event filtered)
 * against that source. On every reconciliation it ensures a {@code ConfigMap} secondary exists with
 * the value dictated by the selected {@link Operation}, using {@code context.resourceOperations()}.
 *
 * <p>The operations are applied idempotently: the secondary is only written when missing or when
 * its data differs from the desired value. Together with own event filtering this keeps the
 * controller from looping on the secondary writes it performs itself.
 */
@ControllerConfiguration
public class SecondaryResourceOperationsReconciler
    implements Reconciler<SecondaryResourceOperationsCustomResource> {

  public static final String DATA_KEY = "value";
  public static final String CREATE_VALUE = "created";
  public static final String APPLIED_VALUE = "applied";

  public enum Operation {
    CREATE,
    SSA,
    UPDATE,
    JSON_PATCH,
    JSON_MERGE_PATCH
  }

  private volatile Operation operation;
  final AtomicInteger numberOfExecutions = new AtomicInteger();

  private InformerEventSource<ConfigMap, SecondaryResourceOperationsCustomResource>
      configMapEventSource;

  @Override
  public UpdateControl<SecondaryResourceOperationsCustomResource> reconcile(
      SecondaryResourceOperationsCustomResource resource,
      Context<SecondaryResourceOperationsCustomResource> context) {
    numberOfExecutions.incrementAndGet();
    var ops = context.resourceOperations();
    var actual = context.getSecondaryResource(ConfigMap.class).orElse(null);

    if (operation == Operation.CREATE) {
      if (actual == null) {
        ops.create(
            desiredConfigMap(resource, CREATE_VALUE), configMapEventSource, Options.cacheOnly());
      }
      return UpdateControl.noUpdate();
    }

    // for the update/patch variants the secondary must exist first
    if (actual == null) {
      ops.create(
          desiredConfigMap(resource, CREATE_VALUE), configMapEventSource, Options.cacheOnly());
      return UpdateControl.noUpdate();
    }

    // idempotency guard: only write when the secondary does not yet hold the desired value
    if (APPLIED_VALUE.equals(actual.getData().get(DATA_KEY))) {
      return UpdateControl.noUpdate();
    }

    switch (operation) {
      case SSA -> {
        var desired = desiredConfigMap(resource, APPLIED_VALUE);
        desired.getMetadata().setResourceVersion(null);
        ops.serverSideApply(desired, configMapEventSource, Options.forceFilterEvents());
      }
      case UPDATE -> {
        actual.getData().put(DATA_KEY, APPLIED_VALUE);
        ops.update(actual, configMapEventSource, Options.filterWithOptimisticLocking());
      }
      case JSON_PATCH ->
          ops.jsonPatch(
              actual,
              cm -> {
                cm.getData().put(DATA_KEY, APPLIED_VALUE);
                return cm;
              },
              configMapEventSource,
              Options.filterWithOptimisticLocking());
      case JSON_MERGE_PATCH -> {
        var desired = desiredConfigMap(resource, APPLIED_VALUE);
        desired.getMetadata().setResourceVersion(actual.getMetadata().getResourceVersion());
        ops.jsonMergePatch(desired, configMapEventSource, Options.filterWithOptimisticLocking());
      }
      default -> throw new IllegalStateException("Unexpected operation: " + operation);
    }
    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, SecondaryResourceOperationsCustomResource>> prepareEventSources(
      EventSourceContext<SecondaryResourceOperationsCustomResource> context) {
    configMapEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, SecondaryResourceOperationsCustomResource.class)
                .build(),
            context);
    return List.of(configMapEventSource);
  }

  private static ConfigMap desiredConfigMap(
      SecondaryResourceOperationsCustomResource primary, String value) {
    var cm =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(primary.getMetadata().getName())
                    .withNamespace(primary.getMetadata().getNamespace())
                    .build())
            .withData(Map.of(DATA_KEY, value))
            .build();
    cm.addOwnerReference(primary);
    return cm;
  }

  public void setOperation(Operation operation) {
    this.operation = operation;
  }
}
