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
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

class NOOPPrimaryToSecondaryIndex<R extends HasMetadata> implements PrimaryToSecondaryIndex<R> {

  @SuppressWarnings("rawtypes")
  private static final NOOPPrimaryToSecondaryIndex instance = new NOOPPrimaryToSecondaryIndex();

  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata> NOOPPrimaryToSecondaryIndex<T> getInstance() {
    return instance;
  }

  private NOOPPrimaryToSecondaryIndex() {}

  @Override
  public void onAddOrUpdate(R resource) {
    // empty method because of noop implementation
  }

  @Override
  public void onDelete(R resource) {
    // empty method because of noop implementation
  }

  @Override
  public Set<ResourceID> getSecondaryResources(ResourceID primary) {
    throw new UnsupportedOperationException();
  }
}
