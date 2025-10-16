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

import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;

/**
 * Manages dynamic number of resources created for a primary resource. A dependent resource
 * implementing this interface will typically also implement one or more additional interfaces such
 * as {@link Creator}, {@link Updater}, {@link Deleter}.
 *
 * @param <R> the dependent resource type
 * @param <P> the primary resource type
 */
public interface BulkDependentResource<R, P extends HasMetadata, ID> {

  /**
   * Retrieves a map of desired secondary resources associated with the specified primary resource,
   * identified by an arbitrary key.
   *
   * @param primary the primary resource with which we want to identify which secondary resources
   *     are associated
   * @param context the {@link Context} associated with the current reconciliation
   * @return a Map associating desired secondary resources with the specified primary via arbitrary
   *     identifiers
   */
  default Map<ID, R> desiredResources(P primary, Context<P> context) {
    throw new IllegalStateException(
        "Implement desiredResources in case a non read-only bulk dependent resource");
  }

  /**
   * Retrieves the actual secondary resources currently existing on the server and associated with
   * the specified primary resource.
   *
   * @param primary the primary resource for which we want to retrieve the associated secondary
   *     resources
   * @param context the {@link Context} associated with the current reconciliation
   * @return a Map associating actual secondary resources with the specified primary via arbitrary
   *     identifiers
   */
  Map<ID, R> getSecondaryResources(P primary, Context<P> context);

  /**
   * Deletes the actual resource identified by the specified key if it's not in the set of desired
   * secondary resources for the specified primary.
   *
   * @param primary the primary resource for which we want to remove now undesired secondary
   *     resources still present on the cluster
   * @param resource the actual resource existing on the cluster that needs to be removed
   * @param key key of the resource
   * @param context actual context
   */
  void deleteTargetResource(P primary, R resource, ID key, Context<P> context);

  /**
   * Determines whether the specified secondary resource matches the desired state with target index
   * of a bulk resource as defined from the specified primary resource, given the specified {@link
   * Context}.
   *
   * @param actualResource the resource we want to determine whether it's matching the desired state
   * @param desired the resource's desired state
   * @param primary the primary resource from which the desired state is inferred
   * @param context the context in which the resource is being matched
   * @return a {@link Result} encapsulating whether the resource matched its desired state and this
   *     associated state if it was computed as part of the matching process. Use the static
   *     convenience methods ({@link Result#nonComputed(boolean)} and {@link
   *     Result#computed(boolean, Object)})
   */
  default Result<R> match(R actualResource, R desired, P primary, Context<P> context) {
    return Matcher.Result.computed(desired.equals(actualResource), desired);
  }
}
