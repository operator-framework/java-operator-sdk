package io.javaoperatorsdk.operator.processing;

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
}
