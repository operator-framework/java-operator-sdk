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
package io.javaoperatorsdk.operator.dependent.multiplemanageddependentsametype;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import static io.javaoperatorsdk.operator.dependent.multiplemanageddependentsametype.MultipleManagedDependentResourceReconciler.CONFIG_MAP_EVENT_SOURCE;

@Workflow(
    dependents = {
      @Dependent(
          type = MultipleManagedDependentResourceConfigMap1.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
      @Dependent(
          type = MultipleManagedDependentResourceConfigMap2.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE)
    })
@ControllerConfiguration
public class MultipleManagedDependentResourceReconciler
    implements Reconciler<MultipleManagedDependentResourceCustomResource>,
        TestExecutionInfoProvider {

  public static final String CONFIG_MAP_EVENT_SOURCE = "ConfigMapEventSource";
  public static final String DATA_KEY = "key";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  public MultipleManagedDependentResourceReconciler() {}

  @Override
  public UpdateControl<MultipleManagedDependentResourceCustomResource> reconcile(
      MultipleManagedDependentResourceCustomResource resource,
      Context<MultipleManagedDependentResourceCustomResource> context) {
    numberOfExecutions.getAndIncrement();

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource<?, MultipleManagedDependentResourceCustomResource>> prepareEventSources(
      EventSourceContext<MultipleManagedDependentResourceCustomResource> context) {
    InformerEventSource<ConfigMap, MultipleManagedDependentResourceCustomResource> ies =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, MultipleManagedDependentResourceCustomResource.class)
                .withName(CONFIG_MAP_EVENT_SOURCE)
                .build(),
            context);
    return List.of(ies);
  }
}
