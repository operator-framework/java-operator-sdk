package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Optional;

import io.fabric8.kubernetes.api.Pluralize;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;

public class GroupVersionKindPlural extends GroupVersionKind {
  private final String plural;

  public GroupVersionKindPlural(String group, String version, String kind, String plural) {
    super(group, version, kind);
    this.plural = plural;
  }

  public GroupVersionKindPlural(GroupVersionKind gvk) {
    this(gvk.getGroup(), gvk.getVersion(), gvk.getKind(),
        gvk instanceof GroupVersionKindPlural ? ((GroupVersionKindPlural) gvk).plural : null);
  }

  public static GroupVersionKind gvkFor(String group, String version, String kind) {
    return new GroupVersionKind(group, version, kind);
  }

  public static GroupVersionKind gvkFor(String apiVersion, String kind) {
    return new GroupVersionKind(apiVersion, kind);
  }

  public static GroupVersionKindPlural gvkFor(Class<? extends HasMetadata> resourceClass) {
    return new GroupVersionKindPlural(HasMetadata.getGroup(resourceClass),
        HasMetadata.getVersion(resourceClass), HasMetadata.getKind(resourceClass),
        HasMetadata.getPlural(resourceClass));
  }

  /**
   * Returns the plural form associated with the kind if it has been provided explicitly (either
   * manually by the user, or determined from the associated resource class definition)
   *
   * @return {@link Optional#empty()} if the plural form was not provided explicitly, or the plural
   *         form if it was provided explicitly
   */
  public Optional<String> getPlural() {
    return Optional.ofNullable(plural);
  }

  /**
   * Returns the plural form associated with the kind if it was provided or a default, computed form
   * via {@link Pluralize#toPlural(String)} (which should correspond to the actual plural form in
   * most cases but might not always be correct, especially if the resource's creator defined an
   * exotic plural form via the CRD.
   *
   * @return the plural form associated with the kind if provided or a default plural form otherwise
   */
  public String getPluralOrDefault() {
    return getPlural().orElse(Pluralize.toPlural(getKind()));
  }
}
