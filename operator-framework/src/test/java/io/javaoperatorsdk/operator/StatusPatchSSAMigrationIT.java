package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.statuspatchnonlocking.StatusPatchLockingCustomResource;
import io.javaoperatorsdk.operator.sample.statuspatchnonlocking.StatusPatchLockingCustomResourceSpec;
import io.javaoperatorsdk.operator.sample.statuspatchnonlocking.StatusPatchLockingReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class StatusPatchSSAMigrationIT {

  public static final String TEST_RESOURCE_NAME = "test";

  private final KubernetesClient client = new KubernetesClientBuilder().build();
  private String testNamespace;

  @BeforeEach
  void beforeEach(TestInfo testInfo) {
    LocallyRunOperatorExtension.applyCrd(StatusPatchLockingCustomResource.class,
        client);
    testInfo.getTestMethod()
        .ifPresent(method -> testNamespace = KubernetesResourceUtil.sanitizeName(method.getName()));
    client.namespaces().resource(testNamespace(testNamespace)).create();
  }

  @AfterEach
  void afterEach() {
    client.namespaces().withName(testNamespace).delete();
    await().untilAsserted(() -> {
      var ns = client.namespaces().withName(testNamespace).get();
      assertThat(ns).isNull();
    });
    client.close();
  }

  @Test
  void testMigratingFromNonSSAToSSA() {
    var operator = startOperator(false);
    var testResource = client.resource(testResource()).create();

    await().untilAsserted(() -> {
      var res = client.resource(testResource).get();
      assertThat(res.getStatus()).isNotNull();
      assertThat(res.getStatus().getMessage()).isEqualTo(StatusPatchLockingReconciler.MESSAGE);
      assertThat(res.getStatus().getValue()).isEqualTo(1);
    });
    operator.stop();

    // start operator with SSA
    operator = startOperator(true);
    await().untilAsserted(() -> {
      var res = client.resource(testResource).get();
      assertThat(res.getStatus()).isNotNull();
      assertThat(res.getStatus().getMessage()).isEqualTo(StatusPatchLockingReconciler.MESSAGE);
      assertThat(res.getStatus().getValue()).isEqualTo(2);
    });

    var actualResource = client.resource(testResource()).get();
    actualResource.getSpec().setMessageInStatus(false);
    client.resource(actualResource).update();

    await().untilAsserted(() -> {
      var res = client.resource(testResource).get();
      assertThat(res.getStatus()).isNotNull();
      assertThat(res.getStatus().getMessage()).isNull();
      assertThat(res.getStatus().getValue()).isEqualTo(3);
    });

    client.resource(testResource()).delete();
  }


  private Operator startOperator(boolean patchStatusWithSSA) {
    var operator = new Operator(o -> o.withCloseClientOnStop(false)
        .withUseSSAForResourceStatusPatch(patchStatusWithSSA));
    operator.register(new StatusPatchLockingReconciler(),
        o -> o.settingNamespaces(testNamespace));

    operator.start();
    return operator;
  }

  StatusPatchLockingCustomResource testResource() {
    StatusPatchLockingCustomResource res = new StatusPatchLockingCustomResource();
    res.setSpec(new StatusPatchLockingCustomResourceSpec());
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE_NAME)
        .withNamespace(testNamespace)
        .build());
    return res;
  }

  private Namespace testNamespace(String name) {
    return new NamespaceBuilder().withMetadata(new ObjectMetaBuilder()
        .withName(name)
        .build()).build();
  }
}
