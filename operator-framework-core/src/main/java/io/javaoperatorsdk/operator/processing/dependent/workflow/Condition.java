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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface Condition<R, P extends HasMetadata> {

  enum Type {
    ACTIVATION,
    DELETE,
    READY,
    RECONCILE
  }

  /**
   * Checks whether a condition holds true for a given {@link
   * io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource} based on the observed
   * cluster state.
   *
   * @param dependentResource for which the condition applies to
   * @param primary the primary resource being considered
   * @param context the current reconciliation {@link Context}
   * @return {@code true} if the condition holds, {@code false} otherwise
   */
  boolean isMet(DependentResource<R, P> dependentResource, P primary, Context<P> context);
}
