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
package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

class SingleDependentResourceReconciler<R, P extends HasMetadata>
    implements DependentResourceReconciler<R, P> {

  private final AbstractDependentResource<R, P> instance;

  SingleDependentResourceReconciler(AbstractDependentResource<R, P> dependentResource) {
    this.instance = dependentResource;
  }

  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    final var maybeActual = instance.getSecondaryResource(primary, context);
    return instance.reconcile(primary, maybeActual.orElse(null), context);
  }

  @Override
  public void delete(P primary, Context<P> context) {
    var secondary = instance.getSecondaryResource(primary, context);
    instance.handleDelete(primary, secondary.orElse(null), context);
  }
}
