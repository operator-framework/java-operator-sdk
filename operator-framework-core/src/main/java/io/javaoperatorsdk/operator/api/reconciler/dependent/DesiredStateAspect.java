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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Context;

/**
 * A cross-cutting hook applied to the desired state of every Kubernetes {@link DependentResource}
 * managed by the operator, typically used to add common metadata (labels or annotations) marking
 * the resources the operator manages.
 *
 * <p>Aspects are registered globally on the {@link ConfigurationService} and are applied, in
 * registration order, right after the desired state has been computed and before it is matched
 * against, created or updated. This means modifications performed by an aspect are taken into
 * account when determining whether the actual resource matches its desired state, so that changing
 * an aspect triggers an update of the associated secondary resources.
 *
 * <p>The desired state is computed at most once per reconciliation and cached in the {@link
 * Context}, so aspects are also called at most once per dependent resource and reconciliation.
 * Aspects are only applied to dependent resources whose desired state is a {@link HasMetadata},
 * i.e. they are not called for external (non-Kubernetes) dependent resources.
 *
 * <p>Implementations are expected to mutate the provided desired state in place and must be
 * thread-safe as they can be called concurrently for different primary resources.
 *
 * @see ConfigurationService#desiredStateAspects()
 */
@FunctionalInterface
public interface DesiredStateAspect {

  /**
   * Applies this aspect to the specified, freshly computed desired state.
   *
   * @param desired the desired state to modify in place
   * @param dependentResource the {@link DependentResource} the desired state was computed for
   * @param context the {@link Context} of the current reconciliation, from which the primary
   *     resource can be retrieved using {@link Context#getPrimaryResource()}
   */
  void apply(HasMetadata desired, DependentResource<?, ?> dependentResource, Context<?> context);
}
