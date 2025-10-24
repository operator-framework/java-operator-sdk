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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.ResourceIDMapper;

public interface ResourceIDMapperBulkDependentResource<R, P extends HasMetadata, ID>
    extends BulkDependentResource<R, P, ID>, DependentResource<R, P> {

  ResourceIDMapper<R, ID> resourceIDMapper();

  @Override
  default Map<ID, R> getSecondaryResources(P primary, Context<P> context) {
    return context
        .getSecondaryResourcesAsStream(resourceType())
        .filter(secondaryResourceFilter(primary, context))
        .collect(Collectors.toMap(cm -> resourceIDMapper().idFor(cm), Function.identity()));
  }

  /**
   * Override of not all the secondary resources of a certain type are related to the target
   * secondary resource.
   *
   * @param primary resource
   * @param context of reconciliation
   * @return predicate to filter secondary resources which are related to the bulk dependent.
   */
  default Predicate<R> secondaryResourceFilter(P primary, Context<P> context) {
    return r -> true;
  }
}
