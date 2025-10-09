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
package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.api.model.Cluster;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;

public class CustomResourceUtils {

  private CustomResourceUtils() {}

  /**
   * Applies internal validations that may not be handled by the fabric8 client.
   *
   * @param resClass Custom Resource to validate
   * @param crd CRD for the Custom Resource
   * @throws OperatorException when the Custom Resource has validation error
   */
  public static void assertCustomResource(Class<?> resClass, CustomResourceDefinition crd) {
    var namespaced = Namespaced.class.isAssignableFrom(resClass);

    if (!namespaced && Namespaced.class.getSimpleName().equals(crd.getSpec().getScope())) {
      throw new OperatorException(
          "Custom resource '"
              + resClass.getName()
              + "' must implement '"
              + Namespaced.class.getName()
              + "' since CRD '"
              + crd.getMetadata().getName()
              + "' is scoped as 'Namespaced'");
    } else if (namespaced && Cluster.class.getSimpleName().equals(crd.getSpec().getScope())) {
      throw new OperatorException(
          "Custom resource '"
              + resClass.getName()
              + "' must not implement '"
              + Namespaced.class.getName()
              + "' since CRD '"
              + crd.getMetadata().getName()
              + "' is scoped as 'Cluster'");
    }
  }
}
