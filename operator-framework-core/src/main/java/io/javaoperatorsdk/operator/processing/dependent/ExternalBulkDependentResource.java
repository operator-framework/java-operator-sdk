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
import io.javaoperatorsdk.operator.processing.ResourceIDMapper;
import io.javaoperatorsdk.operator.processing.ResourceIDProvider;

/**
 * Specialized interface for bulk dependent resources where resource implement {@link
 * ResourceIDProvider}.
 */
public interface ExternalBulkDependentResource<
        R extends ResourceIDProvider<ID>, P extends HasMetadata, ID>
    extends ResourceIDMapperBulkDependentResource<R, P, ID> {

  default ResourceIDMapper<R, ID> resourceIDMapper() {
    return ResourceIDMapper.resourceIdProviderMapper();
  }
}
