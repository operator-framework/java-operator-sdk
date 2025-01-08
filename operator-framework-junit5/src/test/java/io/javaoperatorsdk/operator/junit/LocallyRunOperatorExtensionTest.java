package io.javaoperatorsdk.operator.junit;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import static org.junit.jupiter.api.Assertions.*;

class LocallyRunOperatorExtensionTest {

  @Test
  void getAdditionalCRDsFromFiles() {
    final var crds = LocallyRunOperatorExtension.getAdditionalCRDsFromFiles(
        List.of("src/test/resources/crd/test.crd", "src/test/crd/test.crd"),
        new KubernetesClientBuilder().build());
    assertNotNull(crds);
    assertEquals(2, crds.size());
    assertEquals("src/test/crd/test.crd", crds.get("externals.crd.example"));
    assertEquals("src/test/resources/crd/test.crd", crds.get("tests.crd.example"));
  }
}
