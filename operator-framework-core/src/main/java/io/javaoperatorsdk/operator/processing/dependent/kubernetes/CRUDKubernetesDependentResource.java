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
package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;

/**
 * Adaptor class resources that manage Create, Read and Update operations and that should be
 * automatically garbage-collected by Kubernetes when the associated primary resource is destroyed.
 *
 * @param <R> the type of the managed dependent resource
 * @param <P> the type of the associated primary resource
 */
@Ignore
public abstract class CRUDKubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends KubernetesDependentResource<R, P>
    implements Creator<R, P>, Updater<R, P>, GarbageCollected<P> {

  public CRUDKubernetesDependentResource() {}

  public CRUDKubernetesDependentResource(Class<R> resourceType) {
    super(resourceType);
  }

  public CRUDKubernetesDependentResource(Class<R> resourceType, String name) {
    super(resourceType, name);
  }
}
