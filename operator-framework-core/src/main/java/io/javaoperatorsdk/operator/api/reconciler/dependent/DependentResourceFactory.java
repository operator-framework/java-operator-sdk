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
package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ConfiguredDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependentResourceNode;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface DependentResourceFactory<
    C extends ControllerConfiguration<?>, D extends DependentResourceSpec> {

  DependentResourceFactory DEFAULT = new DependentResourceFactory() {};

  default DependentResource createFrom(D spec, C controllerConfiguration) {
    final var dependentResourceClass = spec.getDependentResourceClass();
    return Utils.instantiateAndConfigureIfNeeded(
        dependentResourceClass,
        DependentResource.class,
        Utils.contextFor(controllerConfiguration, dependentResourceClass, Dependent.class),
        (instance) -> configure(instance, spec, controllerConfiguration));
  }

  default void configure(DependentResource instance, D spec, C controllerConfiguration) {
    if (instance instanceof ConfiguredDependentResource configurable) {
      final var config = controllerConfiguration.getConfigurationFor(spec);
      if (config != null) {
        configurable.configureWith(config);
      }
    }
  }

  default Class<?> associatedResourceType(D spec) {
    final var dependentResourceClass = spec.getDependentResourceClass();
    final var dr =
        Utils.instantiateAndConfigureIfNeeded(
            dependentResourceClass, DependentResource.class, null, null);
    return dr != null ? dr.resourceType() : null;
  }

  default DependentResourceNode createNodeFrom(D spec, DependentResource dependentResource) {
    return new DependentResourceNode(
        spec.getReconcileCondition(),
        spec.getDeletePostCondition(),
        spec.getReadyCondition(),
        spec.getActivationCondition(),
        dependentResource);
  }
}
