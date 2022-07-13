package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class GroupVersionKind {
  public final String group;
  public final String version;
  public final String kind;

  GroupVersionKind(String group, String version, String kind) {
    this.group = group;
    this.version = version;
    this.kind = kind;
  }

  public static GroupVersionKind gvkFor(Class<? extends HasMetadata> resourceClass) {
    return new GroupVersionKind(HasMetadata.getGroup(resourceClass),
        HasMetadata.getVersion(resourceClass), HasMetadata.getKind(resourceClass));
  }
}
