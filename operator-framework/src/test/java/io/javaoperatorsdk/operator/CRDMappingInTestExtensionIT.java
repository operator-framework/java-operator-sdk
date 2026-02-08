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
package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaPropsBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Custom CRD Mapping in Test Extension",
    description =
        """
        Demonstrates how to manually specify and apply Custom Resource Definitions (CRDs) in \
        integration tests using the LocallyRunOperatorExtension. This test verifies that CRDs \
        can be loaded from specified file paths and properly registered with the Kubernetes API \
        server during test execution. It also verifies that CustomResourceDefinition instances
        with no corresponding file can be applied.
        """)
public class CRDMappingInTestExtensionIT {
  private final KubernetesClient client = new KubernetesClientBuilder().build();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new TestReconciler())
          .withAdditionalCRD("src/test/resources/crd/test.crd", "src/test/crd/test.crd")
          .withAdditionalCustomResourceDefinition(testCRD())
          .build();

  public static CustomResourceDefinition testCRD() {
    return new CustomResourceDefinitionBuilder()
        .editOrNewSpec()
        .withScope("Cluster")
        .withGroup("operator.javaoperatorsdk.io")
        .editOrNewNames()
        .withPlural("tests")
        .withSingular("test")
        .withKind("Test")
        .endNames()
        .addNewVersion()
        .withName("v1")
        .withServed(true)
        .withStorage(true)
        .withNewSchema()
        .withNewOpenAPIV3Schema()
        .withType("object")
        .withProperties(Map.of("bar", new JSONSchemaPropsBuilder().withType("string").build()))
        .endOpenAPIV3Schema()
        .endSchema()
        .endVersion()
        .and()
        .editOrNewMetadata()
        .withName("tests.operator.javaoperatorsdk.io")
        .and()
        .build();
  }

  @Test
  void correctlyAppliesManuallySpecifiedCRD() {
    final var crdClient = client.apiextensions().v1().customResourceDefinitions();
    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(() -> assertCrdApplied(crdClient, "tests.crd.example", "foo"));
    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> assertCrdApplied(crdClient, "tests.operator.javaoperatorsdk.io", "bar"));
    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> assertThat(crdClient.withName("externals.crd.example").get()).isNotNull());
  }

  private static void assertCrdApplied(
      NonNamespaceOperation<
              CustomResourceDefinition,
              CustomResourceDefinitionList,
              Resource<CustomResourceDefinition>>
          crdClient,
      String s,
      String propertyName) {
    final var actual = crdClient.withName(s).get();
    assertThat(actual).isNotNull();
    assertThat(
            actual
                .getSpec()
                .getVersions()
                .get(0)
                .getSchema()
                .getOpenAPIV3Schema()
                .getProperties()
                .containsKey(propertyName))
        .isTrue();
  }

  @Group("crd.example")
  @Version("v1")
  @Kind("Test")
  private static class TestCR extends CustomResource<Void, Void> implements Namespaced {}

  @ControllerConfiguration
  private static class TestReconciler implements Reconciler<TestCR> {
    @Override
    public UpdateControl<TestCR> reconcile(TestCR resource, Context<TestCR> context)
        throws Exception {
      return null;
    }
  }
}
