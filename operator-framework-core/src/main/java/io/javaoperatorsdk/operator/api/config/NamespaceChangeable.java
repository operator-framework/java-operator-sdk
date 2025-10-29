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
package io.javaoperatorsdk.operator.api.config;

import java.util.Set;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

public interface NamespaceChangeable {

  /**
   * If the controller and possibly registered {@link
   * io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource} watches a set
   * of namespaces this set can be adjusted dynamically, this when the operator is running.
   *
   * @param namespaces target namespaces to watch
   */
  void changeNamespaces(Set<String> namespaces);

  @SuppressWarnings("unused")
  default void changeNamespaces(String... namespaces) {
    changeNamespaces(namespaces != null ? Set.of(namespaces) : DEFAULT_NAMESPACES_SET);
  }

  default boolean allowsNamespaceChanges() {
    return true;
  }
}
