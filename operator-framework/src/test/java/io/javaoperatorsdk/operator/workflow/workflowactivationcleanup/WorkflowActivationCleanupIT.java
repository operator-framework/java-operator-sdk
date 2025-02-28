package io.javaoperatorsdk.operator.workflow.workflowactivationcleanup;

import org.junit.jupiter.api.*;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WorkflowActivationCleanupIT {

  private final KubernetesClient client = new KubernetesClientBuilder().build();
  private Operator operator;

  private String testNamespace;

  @BeforeEach
  void beforeEach(TestInfo testInfo) {
    LocallyRunOperatorExtension.applyCrd(WorkflowActivationCleanupCustomResource.class, client);

    testInfo
        .getTestMethod()
        .ifPresent(method -> testNamespace = KubernetesResourceUtil.sanitizeName(method.getName()));
    client.namespaces().resource(testNamespace(testNamespace)).create();
    operator = new Operator(o -> o.withCloseClientOnStop(false));
    operator.register(
        new WorkflowActivationCleanupReconciler(), o -> o.settingNamespaces(testNamespace));
  }

  @AfterEach
  void stopOperator() {
    client.namespaces().withName(testNamespace).delete();
    await()
        .untilAsserted(
            () -> {
              var ns = client.namespaces().withName(testNamespace).get();
              assertThat(ns).isNull();
            });
    operator.stop();
  }

  @Test
  void testCleanupOnMarkedResourceOnOperatorStartup() {
    var resource = client.resource(testResourceWithFinalizer()).create();
    client.resource(resource).delete();
    operator.start();

    await()
        .untilAsserted(
            () -> {
              var res = client.resource(resource).get();
              assertThat(res).isNull();
            });
  }

  private WorkflowActivationCleanupCustomResource testResourceWithFinalizer() {
    var resource = new WorkflowActivationCleanupCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("test1")
            .withFinalizers(
                "workflowactivationcleanupcustomresources.sample.javaoperatorsdk/finalizer")
            .withNamespace(testNamespace)
            .build());
    resource.setSpec(new WorkflowActivationCleanupSpec());
    resource.getSpec().setValue("val1");
    return resource;
  }

  private Namespace testNamespace(String name) {
    return new NamespaceBuilder()
        .withMetadata(new ObjectMetaBuilder().withName(name).build())
        .build();
  }
}
