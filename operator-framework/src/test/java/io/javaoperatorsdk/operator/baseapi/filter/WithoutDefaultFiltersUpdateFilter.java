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
package io.javaoperatorsdk.operator.baseapi.filter;

import io.javaoperatorsdk.operator.processing.event.source.controller.InternalEventFilters;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public class WithoutDefaultFiltersUpdateFilter implements OnUpdateFilter<FilterTestCustomResource> {

  private final OnUpdateFilter<FilterTestCustomResource> composed =
      InternalEventFilters.<FilterTestCustomResource>onUpdateGenerationAware(true)
          .or(
              (newResource, oldResource) -> {
                var annotations = newResource.getMetadata().getAnnotations();
                return annotations != null
                    && "true"
                        .equals(
                            annotations.get(WithoutDefaultFiltersReconciler.TRIGGER_ANNOTATION));
              });

  @Override
  public boolean accept(
      FilterTestCustomResource newResource, FilterTestCustomResource oldResource) {
    return composed.accept(newResource, oldResource);
  }
}
