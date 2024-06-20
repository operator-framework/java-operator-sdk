package io.javaoperatorsdk.operator.processing;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class GroupVersionKind {
  private final String group;
  private final String version;
  private final String kind;
  private final String apiVersion;
  protected final static Map<Class<? extends HasMetadata>, GroupVersionKind> CACHE =
      new ConcurrentHashMap<>();

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
    this.apiVersion = apiVersion;
  }

  public static GroupVersionKind gvkFor(Class<? extends HasMetadata> resourceClass) {
    return CACHE.computeIfAbsent(resourceClass, GroupVersionKind::computeGVK);
  }

  private static GroupVersionKind computeGVK(Class<? extends HasMetadata> rc) {
    return new GroupVersionKind(HasMetadata.getGroup(rc),
        HasMetadata.getVersion(rc), HasMetadata.getKind(rc));
  }

  public GroupVersionKind(String group, String version, String kind) {
    this.group = group;
    this.version = version;
    this.kind = kind;
    this.apiVersion = (group == null || group.isBlank()) ? version : group + "/" + version;
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
    return apiVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    GroupVersionKind that = (GroupVersionKind) o;
    return Objects.equals(apiVersion, that.apiVersion) && Objects.equals(kind, that.kind);
  }

  @Override
  public int hashCode() {
    return Objects.hash(apiVersion, kind);
  }

  @Override
  public String toString() {
    return "GroupVersionKind{" +
        "apiVersion='" + apiVersion + '\'' +
        ", kind='" + kind + '\'' +
        '}';
  }

}
