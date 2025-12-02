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
package io.javaoperatorsdk.operator.junit;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import static org.junit.jupiter.api.Assertions.*;

class LocallyRunOperatorExtensionTest {

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

  @Test
  void overrideInfrastructureAndUserKubernetesClient() {
    var infrastructureClient = new KubernetesClientBuilder().build();
    var userKubernetesClient = new KubernetesClientBuilder().build();

    LocallyRunOperatorExtension extension =
        LocallyRunOperatorExtension.builder()
            .withInfrastructureKubernetesClient(infrastructureClient)
            .withKubernetesClient(userKubernetesClient)
            .build();

    assertEquals(infrastructureClient, extension.getInfrastructureKubernetesClient());
    assertEquals(userKubernetesClient, extension.getKubernetesClient());
    assertNotEquals(extension.getInfrastructureKubernetesClient(), extension.getKubernetesClient());
  }

  @Test
  void overrideInfrastructureAndVerifyUserKubernetesClientIsTheSame() {
    var infrastructureClient = new KubernetesClientBuilder().build();

    LocallyRunOperatorExtension extension =
        LocallyRunOperatorExtension.builder()
            .withInfrastructureKubernetesClient(infrastructureClient)
            .build();

    assertEquals(infrastructureClient, extension.getInfrastructureKubernetesClient());
    assertEquals(infrastructureClient, extension.getKubernetesClient());
    assertEquals(extension.getInfrastructureKubernetesClient(), extension.getKubernetesClient());
  }

  @Test
  void overrideKubernetesClientAndVerifyInfrastructureClientIsTheSame() {
    var userKubernetesClient = new KubernetesClientBuilder().build();

    LocallyRunOperatorExtension extension =
        LocallyRunOperatorExtension.builder().withKubernetesClient(userKubernetesClient).build();

    assertEquals(userKubernetesClient, extension.getKubernetesClient());
    assertEquals(userKubernetesClient, extension.getInfrastructureKubernetesClient());
    assertEquals(extension.getKubernetesClient(), extension.getInfrastructureKubernetesClient());
  }
}
