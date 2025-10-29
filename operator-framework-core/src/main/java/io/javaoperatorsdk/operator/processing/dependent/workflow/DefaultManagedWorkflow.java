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
package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceReferencer;
import io.javaoperatorsdk.operator.api.reconciler.dependent.NameSetter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

@SuppressWarnings("rawtypes")
public class DefaultManagedWorkflow<P extends HasMetadata> implements ManagedWorkflow<P> {

  private final Set<String> topLevelResources;
  private final Set<String> bottomLevelResources;
  private final List<DependentResourceSpec> orderedSpecs;
  private final boolean hasCleaner;

  protected DefaultManagedWorkflow(List<DependentResourceSpec> orderedSpecs, boolean hasCleaner) {
    this.hasCleaner = hasCleaner;
    topLevelResources = new HashSet<>(orderedSpecs.size());
    bottomLevelResources =
        orderedSpecs.stream().map(DependentResourceSpec::getName).collect(Collectors.toSet());
    this.orderedSpecs = orderedSpecs;
    for (DependentResourceSpec<?, ?, ?> spec : orderedSpecs) {
      // add cycle detection?
      if (spec.getDependsOn().isEmpty()) {
        topLevelResources.add(spec.getName());
      } else {
        for (String dependsOn : spec.getDependsOn()) {
          bottomLevelResources.remove(dependsOn);
        }
      }
    }
  }

  @Override
  @SuppressWarnings("unused")
  public List<DependentResourceSpec> getOrderedSpecs() {
    return orderedSpecs;
  }

  protected Set<String> getTopLevelResources() {
    return topLevelResources;
  }

  protected Set<String> getBottomLevelResources() {
    return bottomLevelResources;
  }

  List<String> nodeNames() {
    return orderedSpecs.stream().map(DependentResourceSpec::getName).collect(Collectors.toList());
  }

  @Override
  public boolean hasCleaner() {
    return hasCleaner;
  }

  @Override
  public boolean isEmpty() {
    return orderedSpecs.isEmpty();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Workflow<P> resolve(KubernetesClient client, ControllerConfiguration<P> configuration) {
    final var alreadyResolved = new HashMap<String, DependentResourceNode>(orderedSpecs.size());
    for (DependentResourceSpec<?, P, ?> spec : orderedSpecs) {
      final var dependentResource = resolve(spec, client, configuration);
      final var node =
          configuration
              .getConfigurationService()
              .dependentResourceFactory()
              .createNodeFrom(spec, dependentResource);
      alreadyResolved.put(dependentResource.name(), node);
      spec.getDependsOn().forEach(depend -> node.addDependsOnRelation(alreadyResolved.get(depend)));
    }

    final var bottom =
        bottomLevelResources.stream().map(alreadyResolved::get).collect(Collectors.toSet());
    final var top =
        topLevelResources.stream().map(alreadyResolved::get).collect(Collectors.toSet());
    return new DefaultWorkflow<>(
        alreadyResolved,
        bottom,
        top,
        configuration.getWorkflowSpec().map(w -> !w.handleExceptionsInReconciler()).orElseThrow(),
        hasCleaner);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private <R> DependentResource<R, P> resolve(
      DependentResourceSpec<R, P, ?> spec,
      KubernetesClient client,
      ControllerConfiguration<P> configuration) {
    final DependentResource<R, P> dependentResource =
        configuration
            .getConfigurationService()
            .dependentResourceFactory()
            .createFrom(spec, configuration);

    final var name = spec.getName();
    if (name != null && !NO_VALUE_SET.equals(name) && dependentResource instanceof NameSetter) {
      ((NameSetter) dependentResource).setName(name);
    }

    spec.getUseEventSourceWithName()
        .ifPresent(
            esName -> {
              if (dependentResource instanceof EventSourceReferencer) {
                ((EventSourceReferencer) dependentResource).useEventSourceWithName(esName);
              } else {
                throw new IllegalStateException(
                    "DependentResource "
                        + spec
                        + " wants to use EventSource named "
                        + esName
                        + " but doesn't implement support for this feature by implementing "
                        + EventSourceReferencer.class.getSimpleName());
              }
            });

    return dependentResource;
  }
}
