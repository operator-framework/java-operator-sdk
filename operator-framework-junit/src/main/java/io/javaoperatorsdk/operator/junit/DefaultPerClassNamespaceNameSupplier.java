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
package io.javaoperatorsdk.operator.junit;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.extension.ExtensionContext;

import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;

import static io.javaoperatorsdk.operator.junit.AbstractOperatorExtension.MAX_NAMESPACE_NAME_LENGTH;
import static io.javaoperatorsdk.operator.junit.DefaultNamespaceNameSupplier.DELIMITER;
import static io.javaoperatorsdk.operator.junit.DefaultNamespaceNameSupplier.RANDOM_SUFFIX_LENGTH;

public class DefaultPerClassNamespaceNameSupplier implements Function<ExtensionContext, String> {

  public static final int MAX_CLASS_NAME_LENGTH =
      MAX_NAMESPACE_NAME_LENGTH - RANDOM_SUFFIX_LENGTH - 1;

  @Override
  public String apply(ExtensionContext context) {
    String className = context.getRequiredTestClass().getSimpleName();
    String namespace =
        className.length() > MAX_CLASS_NAME_LENGTH
            ? className.substring(0, MAX_CLASS_NAME_LENGTH)
            : className;
    namespace += DELIMITER;
    namespace += UUID.randomUUID().toString().substring(0, RANDOM_SUFFIX_LENGTH);
    namespace = KubernetesResourceUtil.sanitizeName(namespace).toLowerCase(Locale.US);
    namespace = namespace.substring(0, Math.min(namespace.length(), MAX_NAMESPACE_NAME_LENGTH));
    return namespace;
  }
}
