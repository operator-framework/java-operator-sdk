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
package io.javaoperatorsdk.operator.dependent.multipledependentresource;

import java.util.List;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class MultipleDependentResourceReconciler
    implements Reconciler<MultipleDependentResourceCustomResource> {

  public static final String FIRST_CONFIG_MAP_ID = "1";
  public static final String SECOND_CONFIG_MAP_ID = "2";

  private final MultipleDependentResourceConfigMap firstDependentResourceConfigMap;
  private final MultipleDependentResourceConfigMap secondDependentResourceConfigMap;

  public MultipleDependentResourceReconciler() {
    firstDependentResourceConfigMap = new MultipleDependentResourceConfigMap(FIRST_CONFIG_MAP_ID);
    secondDependentResourceConfigMap = new MultipleDependentResourceConfigMap(SECOND_CONFIG_MAP_ID);
  }

  @Override
  public UpdateControl<MultipleDependentResourceCustomResource> reconcile(
      MultipleDependentResourceCustomResource resource,
      Context<MultipleDependentResourceCustomResource> context) {
    firstDependentResourceConfigMap.reconcile(resource, context);
    secondDependentResourceConfigMap.reconcile(resource, context);
    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, MultipleDependentResourceCustomResource>> prepareEventSources(
      EventSourceContext<MultipleDependentResourceCustomResource> context) {
    InformerEventSource<ConfigMap, MultipleDependentResourceCustomResource> eventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, MultipleDependentResourceCustomResource.class)
                .build(),
            context);
    firstDependentResourceConfigMap.setEventSource(eventSource);
    secondDependentResourceConfigMap.setEventSource(eventSource);

    return List.of(eventSource);
  }
}
