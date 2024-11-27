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
    assertThrows(IllegalArgumentException.class,
        () -> GroupVersionKind.fromString("api/beta/v1/ConfigMap"));
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
    var gvk =
        GroupVersionKindPlural.gvkWithPlural(new GroupVersionKind("josdk.io", "v1", kind), null);
    assertThat(gvk.getPlural()).isEmpty();
    assertThat(gvk.getPluralOrDefault())
        .isEqualTo(GroupVersionKindPlural.getDefaultPluralFor(kind));
  }

  @Test
  void pluralShouldOverrideDefaultComputedVersionIfProvided() {
    var gvk = GroupVersionKindPlural.gvkWithPlural(new GroupVersionKind("josdk.io", "v1", "MyKind"),
        "MyPlural");
    assertThat(gvk.getPlural()).hasValue("MyPlural");
  }

  @Test
  void encodesGVKToSimpleString() {
    final var deploymentGVK = "apps/v1/Deployment";
    var gvk = GroupVersionKind.fromString(deploymentGVK);
    assertThat(gvk.toSimpleString()).isEqualTo(deploymentGVK);
    assertThat(gvk).isEqualTo(GroupVersionKind.fromString(gvk.toSimpleString()));

    gvk = GroupVersionKind.fromString("v1/ConfigMap");
    assertThat(gvk.toSimpleString()).isEqualTo("v1/ConfigMap");
    assertThat(gvk).isEqualTo(GroupVersionKind.fromString(gvk.toSimpleString()));
  }
}
