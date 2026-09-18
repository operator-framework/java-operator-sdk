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
package io.javaoperatorsdk.operator.baseapi.withoutnamespaceindex;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
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

/**
 * Both informers drop the namespace index: the primary one through the annotation, the secondary
 * one through the builder. Reconciling and resolving the secondary resource have to keep working,
 * since neither reads that index.
 */
@ControllerConfiguration(informer = @Informer(withoutNamespaceIndex = true))
public class WithoutNamespaceIndexTestReconciler
    implements Reconciler<Secret>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final Set<String> secondariesFound = Collections.synchronizedSet(new HashSet<>());

  @Override
  public UpdateControl<Secret> reconcile(Secret resource, Context<Secret> context) {
    numberOfExecutions.addAndGet(1);
    // reads through the secondary informer's cache, the part that would break if the framework
    // relied on the index it just removed
    context
        .getSecondaryResource(ConfigMap.class)
        .ifPresent(configMap -> secondariesFound.add(configMap.getMetadata().getName()));
    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, Secret>> prepareEventSources(EventSourceContext<Secret> context) {
    return List.of(
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(ConfigMap.class, Secret.class)
                .withNamespacesInheritedFromController()
                .withoutNamespaceIndex(true)
                // the ConfigMap of a Secret is the one sharing its name
                .withSecondaryToPrimaryMapper(
                    configMap ->
                        Set.of(
                            new ResourceID(
                                configMap.getMetadata().getName(),
                                configMap.getMetadata().getNamespace())))
                .build()));
  }

  @Override
  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public Set<String> getSecondariesFound() {
    return secondariesFound;
  }
}
