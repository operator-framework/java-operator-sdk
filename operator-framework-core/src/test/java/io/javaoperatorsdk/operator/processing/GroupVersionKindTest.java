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

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GroupVersionKindPlural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class GroupVersionKindTest {

  @Test
  void testInitFromApiVersion() {
    var gvk = new GroupVersionKind("v1", "ConfigMap");
    assertThat(gvk.getGroup()).isNull();
    assertThat(gvk.getVersion()).isEqualTo("v1");

    gvk = new GroupVersionKind("apps/v1", "Deployment");
    assertThat(gvk.getGroup()).isEqualTo("apps");
    assertThat(gvk.getVersion()).isEqualTo("v1");
  }

  @Test
  void parseGVK() {
    var gvk = GroupVersionKind.fromString("apps/v1/Deployment");
    assertThat(gvk.getGroup()).isEqualTo("apps");
    assertThat(gvk.getVersion()).isEqualTo("v1");
    assertThat(gvk.getKind()).isEqualTo("Deployment");

    gvk = GroupVersionKind.fromString("v1/ConfigMap");
    assertThat(gvk.getGroup()).isNull();
    assertThat(gvk.getVersion()).isEqualTo("v1");
    assertThat(gvk.getKind()).isEqualTo("ConfigMap");

    assertThrows(IllegalArgumentException.class, () -> GroupVersionKind.fromString("v1#ConfigMap"));
    assertThrows(
        IllegalArgumentException.class, () -> GroupVersionKind.fromString("api/beta/v1/ConfigMap"));
  }

  @Test
  void pluralShouldOnlyBeProvidedIfExplicitlySet() {
    final var kind = "ConfigMap";
    var gvk = GroupVersionKindPlural.from(new GroupVersionKind("v1", kind));
    assertThat(gvk.getPlural()).isEmpty();
    assertThat(gvk.getPluralOrDefault())
        .isEqualTo(GroupVersionKindPlural.getDefaultPluralFor(kind));

    gvk = GroupVersionKindPlural.from(GroupVersionKind.gvkFor(ConfigMap.class));
    assertThat(gvk.getPlural()).isEmpty();
    assertThat(gvk.getPluralOrDefault()).isEqualTo(HasMetadata.getPlural(ConfigMap.class));

    gvk = GroupVersionKindPlural.gvkFor(ConfigMap.class);
    assertThat(gvk.getPlural()).hasValue(HasMetadata.getPlural(ConfigMap.class));

    gvk = GroupVersionKindPlural.from(gvk);
    assertThat(gvk.getPlural()).hasValue(HasMetadata.getPlural(ConfigMap.class));
  }

  @Test
  void pluralShouldBeEmptyIfNotProvided() {
    final var kind = "MyKind";
    final var original = new GroupVersionKind("josdk.io", "v1", kind);
    var gvk = GroupVersionKindPlural.gvkWithPlural(original, null);
    assertThat(gvk.getPlural()).isEmpty();
    assertThat(gvk.getPluralOrDefault())
        .isEqualTo(GroupVersionKindPlural.getDefaultPluralFor(kind));
    assertThat(gvk).isEqualTo(original);
    assertThat(original).isEqualTo(gvk);
    assertThat(gvk.hashCode()).isEqualTo(original.hashCode());
  }

  @Test
  void pluralShouldOverrideDefaultComputedVersionIfProvided() {
    final var original = new GroupVersionKind("josdk.io", "v1", "MyKind");
    final var gvk = GroupVersionKindPlural.gvkWithPlural(original, "MyPlural");
    assertThat(gvk.getPlural()).hasValue("MyPlural");
    assertThat(gvk).isNotEqualTo(original);
    assertThat(original).isNotEqualTo(gvk);
    assertThat(gvk.hashCode()).isNotEqualTo(original.hashCode());
  }

  @Test
  void equals() {
    final var original = new GroupVersionKind("josdk.io", "v1", "MyKind");
    assertEquals(original, original);
    assertFalse(original.equals(null));
  }

  @Test
  void encodesToGVKString() {
    final var deploymentGVK = "apps/v1/Deployment";
    var gvk = GroupVersionKind.fromString(deploymentGVK);
    assertThat(gvk.toGVKString()).isEqualTo(deploymentGVK);
    assertThat(gvk).isEqualTo(GroupVersionKind.fromString(gvk.toGVKString()));

    gvk = GroupVersionKind.fromString("v1/ConfigMap");
    assertThat(gvk.toGVKString()).isEqualTo("v1/ConfigMap");
    assertThat(gvk).isEqualTo(GroupVersionKind.fromString(gvk.toGVKString()));
  }
}
