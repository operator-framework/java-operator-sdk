package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class CRDMappingInTestExtensionIT {
  private final KubernetesClient client = new KubernetesClientBuilder().build();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new TestReconciler())
          .withAdditionalCRD("src/test/resources/crd/test.crd", "src/test/crd/test.crd")
          .build();

  @Test
  void correctlyAppliesManuallySpecifiedCRD() {
    final var crdClient = client.apiextensions().v1().customResourceDefinitions();
    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              final var actual = crdClient.withName("tests.crd.example").get();
              assertThat(actual).isNotNull();
              assertThat(
                      actual
                          .getSpec()
                          .getVersions()
                          .get(0)
                          .getSchema()
                          .getOpenAPIV3Schema()
                          .getProperties()
                          .containsKey("foo"))
                  .isTrue();
            });
    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> assertThat(crdClient.withName("externals.crd.example").get()).isNotNull());
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
