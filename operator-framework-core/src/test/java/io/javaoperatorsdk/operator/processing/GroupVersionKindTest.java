package io.javaoperatorsdk.operator.processing;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;

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
  void pluralShouldOnlyBeProvidedIfExplicitlySet() {
    var gvk = new GroupVersionKind("v1", "ConfigMap");
    assertThat(gvk.getPlural()).isEmpty();

    gvk = GroupVersionKind.gvkFor(ConfigMap.class);
    assertThat(gvk.getPlural()).hasValue(HasMetadata.getPlural(ConfigMap.class));
  }

}
