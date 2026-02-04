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
package io.javaoperatorsdk.operator.processing.event;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;

public class ResourceID implements Serializable {

  public static ResourceID fromResource(HasMetadata resource) {
    return new ResourceID(resource.getMetadata().getName(), resource.getMetadata().getNamespace());
  }

  public static ResourceID fromOwnerReference(
      HasMetadata resource, OwnerReference ownerReference, boolean clusterScoped) {
    return new ResourceID(
        ownerReference.getName(), clusterScoped ? null : resource.getMetadata().getNamespace());
  }

  private final String name;
  private final String namespace;

  public ResourceID(String name, String namespace) {
    this.name = name;
    this.namespace = namespace;
  }

  public ResourceID(String name) {
    this(name, null);
  }

  public String getName() {
    return name;
  }

  public Optional<String> getNamespace() {
    return Optional.ofNullable(namespace);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResourceID that = (ResourceID) o;
    return Objects.equals(name, that.name) && Objects.equals(namespace, that.namespace);
  }

  /**
   * Whether this ResourceID points to the same resource as the one identified only by the specified
   * name and namespace.
   *
   * <p>Note that this doesn't take API version or Kind into account so this should only be used
   * when checking resources that are reasonably expected to be of the same type.
   *
   * @param name the name of the resource we want to check
   * @param namespace the possibly {@code null} namespace of the resource we want to check
   * @return {@code true} if this resource points to the same resource as the one pointed to by the
   *     specified name and namespace, {@code false} otherwise
   * @since 5.3.0
   */
  public boolean equals(String name, String namespace) {
    return Objects.equals(this.name, name) && Objects.equals(this.namespace, namespace);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, namespace);
  }

  @Override
  public String toString() {
    return toString(name, namespace);
  }

  public static String toString(HasMetadata resource) {
    return toString(resource.getMetadata().getName(), resource.getMetadata().getNamespace());
  }

  private static String toString(String name, String namespace) {
    return "ResourceID{" + "name='" + name + '\'' + ", namespace='" + namespace + '\'' + '}';
  }
}
