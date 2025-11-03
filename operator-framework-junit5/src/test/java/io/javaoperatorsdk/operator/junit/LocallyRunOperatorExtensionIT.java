package io.javaoperatorsdk.operator.junit;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import static org.junit.jupiter.api.Assertions.*;

class LocallyRunOperatorExtensionIT {

  @Test
  void getAdditionalCRDsFromFiles() {
    System.out.println(Path.of("").toAbsolutePath());
    System.out.println(Path.of("src/test/crd/test.crd").toAbsolutePath());
    final var crds =
        LocallyRunOperatorExtension.getAdditionalCRDsFromFiles(
            List.of("src/test/resources/crd/test.crd", "src/test/crd/test.crd"),
            new KubernetesClientBuilder().build());
    assertNotNull(crds);
    assertEquals(2, crds.size());
    assertEquals("src/test/crd/test.crd", crds.get("externals.crd.example"));
    assertEquals("src/test/resources/crd/test.crd", crds.get("tests.crd.example"));
  }
}
