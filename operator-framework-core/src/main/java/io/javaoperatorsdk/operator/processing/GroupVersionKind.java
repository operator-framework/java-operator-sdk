package io.javaoperatorsdk.operator.processing;

import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.Pluralize;
import io.fabric8.kubernetes.api.model.HasMetadata;

public class GroupVersionKind {
  private final String group;
  private final String version;
  private final String kind;
  private final String plural;
  private final String apiVersion;

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
    this.plural = null;
    this.apiVersion = apiVersion;
  }

  public GroupVersionKind(String group, String version, String kind) {
    this(group, version, kind, null);
  }

  public GroupVersionKind(String group, String version, String kind, String plural) {
    this.group = group;
    this.version = version;
    this.kind = kind;
    this.plural = plural;
    this.apiVersion = (group == null || group.isBlank()) ? version : group + "/" + version;
  }

  public static GroupVersionKind gvkFor(Class<? extends HasMetadata> resourceClass) {
    return new GroupVersionKind(HasMetadata.getGroup(resourceClass),
        HasMetadata.getVersion(resourceClass), HasMetadata.getKind(resourceClass),
        HasMetadata.getPlural(resourceClass));
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

  /**
   * Returns the plural form associated with the kind if it has been provided explicitly (either
   * manually by the user, or determined from the associated resource class definition)
   *
   * @return {@link Optional#empty()} if the plural form was not provided explicitly (in which case,
   *         it could be approximated by using {@link Pluralize#toPlural(String)} on the kind), or
   *         the plural form if it was provided explicitly
   */
  public Optional<String> getPlural() {
    return Optional.ofNullable(plural);
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
        "group='" + group + '\'' +
        ", version='" + version + '\'' +
        ", kind='" + kind + '\'' +
        '}';
  }
}
