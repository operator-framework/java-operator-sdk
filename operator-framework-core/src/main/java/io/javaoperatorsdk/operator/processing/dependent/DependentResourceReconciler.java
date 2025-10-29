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

/**
 * An internal interface that abstracts away how to reconcile dependent resources, in particular
 * when they can be dynamically created based on the state provided by the primary resource (e.g.
 * the primary resource dictates which/how many secondary resources need to be created).
 *
 * @param <R> the type of the secondary resource to be reconciled
 * @param <P> the primary resource type
 */
interface DependentResourceReconciler<R, P extends HasMetadata> {

  ReconcileResult<R> reconcile(P primary, Context<P> context);

  void delete(P primary, Context<P> context);
}
