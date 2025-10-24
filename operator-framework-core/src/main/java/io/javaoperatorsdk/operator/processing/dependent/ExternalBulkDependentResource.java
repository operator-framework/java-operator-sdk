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
import io.javaoperatorsdk.operator.processing.ResourceIDProvider;

public interface ExternalBulkDependentResource<
        R extends ResourceIDProvider<ID>, P extends HasMetadata, ID>
    extends BulkDependentResource<R, P, ID>, DependentResource<R, P> {

  /**
   * Since we can list all the related resources and by assuming the resource extends {{@link
   * ResourceIDProvider}} it is trivial to create the target map. The only issue is if there are
   * other secondary resources of the target type which are not managed by this bulk dependent
   * resources, for those it is enough to override secondaryResourceFilter method.
   */
  @Override
  default Map<ID, R> getSecondaryResources(P primary, Context<P> context) {
    return context
        .getSecondaryResourcesAsStream(resourceType())
        .filter(secondaryResourceFilter(primary, context))
        .collect(
            Collectors.toMap(
                cm -> ResourceIDMapper.<R, ID>resourceIdProviderMapper().idFor(cm),
                Function.identity()));
  }

  /**
   * Override if not all the secondary resources of target type are managed by the bulk dependent
   * resource.
   */
  default Predicate<R> secondaryResourceFilter(P primary, Context<P> context) {
    return r -> true;
  }
}
