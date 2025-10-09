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
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;

/**
 * Adaptor class resources that manage Create, Read and Update operations, however resource is NOT
 * garbage collected by Kubernetes when the associated primary resource is destroyed, instead
 * explicitly deleted. This is useful when resource needs to be deleted before another one in a
 * workflow, in other words an ordering matters during a cleanup. See also: <a
 * href="https://github.com/operator-framework/java-operator-sdk/issues/1127">Related issue</a>
 *
 * @param <R> the type of the managed dependent resource
 * @param <P> the type of the associated primary resource
 */
@Ignore
public class CRUDNoGCKubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends KubernetesDependentResource<R, P> implements Creator<R, P>, Updater<R, P>, Deleter<P> {

  public CRUDNoGCKubernetesDependentResource() {}

  public CRUDNoGCKubernetesDependentResource(Class<R> resourceType) {
    super(resourceType);
  }
}
