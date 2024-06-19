package io.javaoperatorsdk.operator.processing;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class GroupVersionKind {
  private final String group;
  private final String version;
  private final String kind;

  public GroupVersionKind(String apiVersion, String kind) {
    this.kind = kind;
    String[] groupAndVersion = apiVersion.split("/");
    if (groupAndVersion.length == 1) {
      this.group = null;
      this.version = groupAndVersion[0];
    } else {
      this.group = groupAndVersion[0];
      this.version = groupAndVersion[1];
    }
  }

  public GroupVersionKind(String group, String version, String kind) {
    this.group = group;
    this.version = version;
    this.kind = kind;
  }

  public static GroupVersionKind gvkFor(Class<? extends HasMetadata> resourceClass) {
    return new GroupVersionKind(HasMetadata.getGroup(resourceClass),
        HasMetadata.getVersion(resourceClass), HasMetadata.getKind(resourceClass));
  }

  /**
   * Parse GVK from a String representation. Expected format is: [group]/[version]/[kind]
   * <p/>
   * Sample: "apps/v1/Deployment"
   * <p/>
   * or: [version]/[kind]
   * <p/>
   * Sample: v1/ConfigMap
   **/
  public static GroupVersionKind fromString(String gvk) {
    String[] parts = gvk.split("/");
    if (parts.length == 3) {
      return new GroupVersionKind(parts[0], parts[1], parts[2]);
    } else if (parts.length == 2) {
      return new GroupVersionKind(null, parts[0], parts[1]);
    } else {
      throw new IllegalArgumentException(
          "Cannot parse gvk: " + gvk + ". Needs to be in form [group]/[version]/[kind]");
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
    return group == null || group.isBlank() ? version : group + "/" + version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    GroupVersionKind that = (GroupVersionKind) o;
    return Objects.equals(group, that.group) && Objects.equals(version, that.version)
        && Objects.equals(kind, that.kind);
  }

  @Override
  public int hashCode() {
    return Objects.hash(group, version, kind);
  }

  @Override
  public String toString() {
    return "GroupVersionKind{" +
        "group='" + group + '\'' +
        ", version='" + version + '\'' +
        ", kind='" + kind + '\'' +
        '}';
  }

}
