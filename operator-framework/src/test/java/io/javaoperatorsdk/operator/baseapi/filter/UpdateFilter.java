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

import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.baseapi.filter.FilterTestReconciler.CUSTOM_RESOURCE_FILTER_VALUE;

public class UpdateFilter implements OnUpdateFilter<FilterTestCustomResource> {
  @Override
  public boolean accept(FilterTestCustomResource resource, FilterTestCustomResource oldResource) {
    return !resource.getSpec().getValue().equals(CUSTOM_RESOURCE_FILTER_VALUE);
  }
}
