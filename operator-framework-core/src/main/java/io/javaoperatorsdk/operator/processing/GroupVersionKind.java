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
package io.javaoperatorsdk.operator.processing;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class GroupVersionKind {
  private static final String SEPARATOR = "/";
  private final String group;
  private final String version;
  private final String kind;
  private final String apiVersion;
  protected static final Map<Class<? extends HasMetadata>, GroupVersionKind> CACHE =
      new ConcurrentHashMap<>();

  public GroupVersionKind(String apiVersion, String kind) {
    this.kind = kind;
    String[] groupAndVersion = apiVersion.split(SEPARATOR);
    if (groupAndVersion.length == 1) {
      this.group = null;
      this.version = groupAndVersion[0];
    } else {
      this.group = groupAndVersion[0];
      this.version = groupAndVersion[1];
    }
    this.apiVersion = apiVersion;
  }

  public static GroupVersionKind gvkFor(Class<? extends HasMetadata> resourceClass) {
    return CACHE.computeIfAbsent(resourceClass, GroupVersionKind::computeGVK);
  }

  private static GroupVersionKind computeGVK(Class<? extends HasMetadata> rc) {
    return new GroupVersionKind(
        HasMetadata.getGroup(rc), HasMetadata.getVersion(rc), HasMetadata.getKind(rc));
  }

  public GroupVersionKind(String group, String version, String kind) {
    this.group = group;
    this.version = version;
    this.kind = kind;
    this.apiVersion = (group == null || group.isBlank()) ? version : group + SEPARATOR + version;
  }

  /**
   * Parse GVK from a String representation. Expected format is: [group]/[version]/[kind]
   *
   * <pre>
   *   Sample: "apps/v1/Deployment"
   * </pre>
   *
   * or: [version]/[kind]
   *
   * <pre>
   *     Sample: v1/ConfigMap
   * </pre>
   */
  public static GroupVersionKind fromString(String gvk) {
    String[] parts = gvk.split(SEPARATOR);
    if (parts.length == 3) {
      return new GroupVersionKind(parts[0], parts[1], parts[2]);
    } else if (parts.length == 2) {
      return new GroupVersionKind(null, parts[0], parts[1]);
    } else {
      throw new IllegalArgumentException(
          "Cannot parse gvk: " + gvk + ". Needs to be in form [group]/[version]/[kind]");
    }
  }

  /**
   * Reverse to {@link #fromString(String)}.
   *
   * @return gvk encoded in simple string.
   */
  public String toGVKString() {
    if (group != null) {
      return group + SEPARATOR + version + SEPARATOR + kind;
    } else {
      return version + SEPARATOR + kind;
    }
  }

  public String getGroup() {
    return group;
  }

  public String getVersion() {
    return version;
  }

  public String getKind() {
    return kind;
  }

  public String apiVersion() {
    return apiVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GroupVersionKind that)) return false;
    return Objects.equals(apiVersion, that.apiVersion)
        && Objects.equals(kind, that.kind)
        && specificEquals(that)
        && that.specificEquals(this);
  }

  protected boolean specificEquals(GroupVersionKind that) {
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(apiVersion, kind);
  }

  @Override
  public String toString() {
    return toGVKString();
  }
}
