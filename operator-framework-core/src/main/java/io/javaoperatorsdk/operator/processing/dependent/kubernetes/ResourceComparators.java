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

import java.util.Objects;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;

public class ResourceComparators {

  public static boolean compareConfigMapData(ConfigMap c1, ConfigMap c2) {
    return Objects.equals(c1.getData(), c2.getData())
        && Objects.equals(c1.getBinaryData(), c2.getBinaryData());
  }

  public static boolean compareSecretData(Secret s1, Secret s2) {
    return Objects.equals(s1.getType(), s2.getType())
        && Objects.equals(s1.getData(), s2.getData())
        && Objects.equals(s1.getStringData(), s2.getStringData());
  }
}
