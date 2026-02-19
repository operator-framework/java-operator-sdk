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
package io.javaoperatorsdk.operator.baseapi.multiplesecondaryeventsource;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class MultipleSecondaryEventSourceReconciler
    implements Reconciler<MultipleSecondaryEventSourceCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  public static String getName1(MultipleSecondaryEventSourceCustomResource resource) {
    return resource.getMetadata().getName() + "1";
  }

  public static String getName2(MultipleSecondaryEventSourceCustomResource resource) {
    return resource.getMetadata().getName() + "2";
  }

  @Override
  public UpdateControl<MultipleSecondaryEventSourceCustomResource> reconcile(
      MultipleSecondaryEventSourceCustomResource resource,
      Context<MultipleSecondaryEventSourceCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    context.resourceOperations().serverSideApply(configMap(getName1(resource), resource));
    context.resourceOperations().serverSideApply(configMap(getName2(resource), resource));

    if (numberOfExecutions.get() >= 3) {
      if (context.getSecondaryResources(ConfigMap.class).size() != 2) {
        throw new IllegalStateException("There should be 2 related config maps");
      }
    }
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource<?, MultipleSecondaryEventSourceCustomResource>> prepareEventSources(
      EventSourceContext<MultipleSecondaryEventSourceCustomResource> context) {

    var config =
        InformerEventSourceConfiguration.from(
                ConfigMap.class, MultipleSecondaryEventSourceCustomResource.class)
            .withNamespacesInheritedFromController()
            .withLabelSelector("multisecondary")
            .withSecondaryToPrimaryMapper(
                s -> {
                  var name =
                      s.getMetadata()
                          .getName()
                          .subSequence(0, s.getMetadata().getName().length() - 1);
                  return Set.of(new ResourceID(name.toString(), s.getMetadata().getNamespace()));
                })
            .build();
    InformerEventSource<ConfigMap, MultipleSecondaryEventSourceCustomResource>
        configMapEventSource = new InformerEventSource<>(config, context);
    return List.of(configMapEventSource);
  }

  ConfigMap configMap(String name, MultipleSecondaryEventSourceCustomResource resource) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(name);
    configMap.getMetadata().setNamespace(resource.getMetadata().getNamespace());
    configMap.setData(new HashMap<>());
    configMap.getData().put(name, name);
    HashMap<String, String> labels = new HashMap<>();
    labels.put("multisecondary", "true");
    configMap.getMetadata().setLabels(labels);
    configMap.addOwnerReference(resource);
    return configMap;
  }
}
