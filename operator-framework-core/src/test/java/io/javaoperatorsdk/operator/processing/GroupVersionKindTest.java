package io.javaoperatorsdk.operator.processing;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GroupVersionKindPlural;

import static org.assertj.core.api.Assertions.assertThat;

class GroupVersionKindTest {

  @Test
  void testInitFromApiVersion() {
    var gvk = GroupVersionKindPlural.gvkFor("v1", "ConfigMap");
    assertThat(gvk.getGroup()).isNull();
    assertThat(gvk.getVersion()).isEqualTo("v1");

    gvk = GroupVersionKindPlural.gvkFor("apps/v1", "Deployment");
    assertThat(gvk.getGroup()).isEqualTo("apps");
    assertThat(gvk.getVersion()).isEqualTo("v1");
  }

  @Test
  void pluralShouldOnlyBeProvidedIfExplicitlySet() {
    final var gvk = GroupVersionKindPlural.gvkFor(ConfigMap.class);
    assertThat(gvk.getPlural()).hasValue(HasMetadata.getPlural(ConfigMap.class));
  }

}
