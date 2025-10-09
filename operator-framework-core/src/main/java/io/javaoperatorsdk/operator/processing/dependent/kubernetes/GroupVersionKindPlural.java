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
package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.Pluralize;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;

/**
 * An extension of {@link GroupVersionKind} that also records the associated plural form which is
 * useful when dealing with Kubernetes RBACs. Downstream projects might leverage that information.
 */
public class GroupVersionKindPlural extends GroupVersionKind {
  private final String plural;

  protected GroupVersionKindPlural(String group, String version, String kind, String plural) {
    super(group, version, kind);
    this.plural = plural;
  }

  protected GroupVersionKindPlural(String apiVersion, String kind, String plural) {
    super(apiVersion, kind);
    this.plural = plural;
  }

  protected GroupVersionKindPlural(GroupVersionKind gvk, String plural) {
    this(
        gvk.getGroup(),
        gvk.getVersion(),
        gvk.getKind(),
        plural != null
            ? plural
            : (gvk instanceof GroupVersionKindPlural
                ? ((GroupVersionKindPlural) gvk).plural
                : null));
  }

  @Override
  protected boolean specificEquals(GroupVersionKind that) {
    if (plural == null) {
      return true;
    }
    return that instanceof GroupVersionKindPlural gvkp && gvkp.plural.equals(plural);
  }

  @Override
  public int hashCode() {
    return plural != null ? Objects.hash(super.hashCode(), plural) : super.hashCode();
  }

  @Override
  public String toString() {
    return toGVKString() + (plural != null ? " (plural: " + plural + ")" : "");
  }

  /**
   * Creates a new GroupVersionKindPlural from the specified {@link GroupVersionKind}.
   *
   * @param gvk a {@link GroupVersionKind} from which to create a new GroupVersionKindPlural object
   * @return a new GroupVersionKindPlural object matching the specified {@link GroupVersionKind}
   */
  public static GroupVersionKindPlural from(GroupVersionKind gvk) {
    return gvk instanceof GroupVersionKindPlural
        ? ((GroupVersionKindPlural) gvk)
        : gvkWithPlural(gvk, null);
  }

  /**
   * Creates a new GroupVersionKindPlural based on the specified {@link GroupVersionKind} instance
   * but specifying a plural form to use as well.
   *
   * @param gvk the base {@link GroupVersionKind} from which to derive a new GroupVersionKindPlural
   * @param plural the plural form to use for the new instance or {@code null} if the default plural
   *     form is desired. Note that the specified plural form will override any existing plural form
   *     for the specified {@link GroupVersionKind} (in particular, if the specified {@link
   *     GroupVersionKind} was already an instance of GroupVersionKindPlural, its plural form will
   *     only be considered in the new instance if the specified plural form is {@code null}
   * @return a new GroupVersionKindPlural derived from the specified {@link GroupVersionKind} and
   *     plural form
   */
  public static GroupVersionKindPlural gvkWithPlural(GroupVersionKind gvk, String plural) {
    return new GroupVersionKindPlural(gvk, plural);
  }

  /**
   * Creates a new GroupVersionKindPlural instance extracting the information from the specified
   * {@link HasMetadata} implementation
   *
   * @param resourceClass the {@link HasMetadata} from which group, version, kind and plural form
   *     are extracted
   * @return a new GroupVersionKindPlural instance based on the specified {@link HasMetadata}
   *     implementation
   */
  public static GroupVersionKindPlural gvkFor(Class<? extends HasMetadata> resourceClass) {
    final var gvk = GroupVersionKind.gvkFor(resourceClass);
    return gvkWithPlural(gvk, HasMetadata.getPlural(resourceClass));
  }

  /**
   * Retrieves the default plural form for the specified kind.
   *
   * @param kind the kind for which we want to get the default plural form
   * @return the default plural form for the specified kind
   */
  public static String getDefaultPluralFor(String kind) {
    // todo: replace by Fabric8 version when available, see
    // https://github.com/fabric8io/kubernetes-client/pull/6314
    return kind != null ? Pluralize.toPlural(kind.toLowerCase()) : null;
  }

  /**
   * Returns the plural form associated with the kind if it has been provided explicitly (either
   * manually by the user, or determined from the associated resource class definition)
   *
   * @return {@link Optional#empty()} if the plural form was not provided explicitly, or the plural
   *     form if it was provided explicitly
   */
  public Optional<String> getPlural() {
    return Optional.ofNullable(plural);
  }

  /**
   * Returns the plural form associated with the kind if it was provided or a default, computed form
   * via {@link #getDefaultPluralFor(String)} (which should correspond to the actual plural form in
   * most cases but might not always be correct, especially if the resource's creator defined an
   * exotic plural form via the CRD.
   *
   * @return the plural form associated with the kind if provided or a default plural form otherwise
   */
  @SuppressWarnings("unused")
  public String getPluralOrDefault() {
    return getPlural().orElse(getDefaultPluralFor(getKind()));
  }
}
