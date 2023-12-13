package io.javaoperatorsdk.operator.processing;

import org.junit.jupiter.api.Test;

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

}
