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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface Workflow<P extends HasMetadata> {

  boolean THROW_EXCEPTION_AUTOMATICALLY_DEFAULT = true;

  default WorkflowReconcileResult reconcile(P primary, Context<P> context) {
    throw new UnsupportedOperationException("Implement this");
  }

  default WorkflowCleanupResult cleanup(P primary, Context<P> context) {
    throw new UnsupportedOperationException("Implement this");
  }

  default boolean hasCleaner() {
    return false;
  }

  default boolean isEmpty() {
    return size() == 0;
  }

  default int size() {
    return getDependentResourcesByName().size();
  }

  @SuppressWarnings("rawtypes")
  default Map<String, DependentResource> getDependentResourcesByName() {
    return Collections.emptyMap();
  }

  @SuppressWarnings("rawtypes")
  default List<DependentResource> getDependentResourcesWithoutActivationCondition() {
    return Collections.emptyList();
  }
}
