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

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;

/**
 * Handles external resources where in order to address the resource additional information or
 * persistent state (usually the ID of the resource) is needed to access the current state. These
 * are non Kubernetes resources which when created their ID is generated, so cannot be determined
 * based only on primary resources. In order to manage such dependent resource use this interface
 * for a resource that extends {@link AbstractExternalDependentResource}.
 *
 * @param <R> the dependent resource type
 * @param <P> the primary resource type
 * @param <S> the state type
 */
public interface DependentResourceWithExplicitState<R, P extends HasMetadata, S extends HasMetadata>
    extends Creator<R, P>, Deleter<P> {

  /**
   * Only needs to be implemented if multiple event sources are present for the target resource
   * class.
   *
   * @return name of the event source to access the state resources.
   */
  default Optional<String> eventSourceName() {
    return Optional.empty();
  }

  /**
   * Class of the state resource.
   *
   * @return the type of the resource that stores state
   */
  Class<S> stateResourceClass();

  /**
   * State resource which contains the target state. Usually an ID to address the resource
   *
   * @param primary resource
   * @param resource secondary resource
   * @return that stores state
   */
  S stateResource(P primary, R resource);
}
