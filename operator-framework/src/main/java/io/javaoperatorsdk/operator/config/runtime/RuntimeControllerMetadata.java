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
package io.javaoperatorsdk.operator.config.runtime;

import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

@SuppressWarnings("rawtypes")
public class RuntimeControllerMetadata {

  public static final String RECONCILERS_RESOURCE_PATH = "javaoperatorsdk/reconcilers";
  private static final Map<Class<? extends Reconciler>, Class<? extends HasMetadata>>
      controllerToCustomResourceMappings;

  static {
    controllerToCustomResourceMappings =
        ClassMappingProvider.provide(
            RECONCILERS_RESOURCE_PATH, Reconciler.class, HasMetadata.class);
  }

  @SuppressWarnings("unchecked")
  static <R extends HasMetadata> Class<R> getResourceClass(Reconciler<R> reconciler) {
    final Class<? extends HasMetadata> resourceClass =
        controllerToCustomResourceMappings.get(reconciler.getClass());
    if (resourceClass == null) {
      throw new IllegalArgumentException(
          String.format(
              "No custom resource has been found for controller %s",
              reconciler.getClass().getCanonicalName()));
    }
    return (Class<R>) resourceClass;
  }
}
