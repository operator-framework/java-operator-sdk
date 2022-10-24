package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.rbacbehavior.RBACBehaviorTestCustomResource;
import io.javaoperatorsdk.operator.sample.rbacbehavior.RBACBehaviorTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RBACBehaviorIT {

  // https://junit.org/junit5/docs/5.1.1/api/org/junit/jupiter/api/extension/TestInstancePostProcessor.html
  // minikube start --extra-config=apiserver.min-request-timeout=3

  KubernetesClient adminClient = new KubernetesClientBuilder().build();
  RBACBehaviorTestReconciler reconciler;
  String actualNamespace;

  @BeforeEach
  void beforeEach(TestInfo testInfo) {
    testInfo.getTestMethod().ifPresent(method -> {
      actualNamespace = KubernetesResourceUtil.sanitizeName(method.getName());
      adminClient.resource(namespace()).createOrReplace();
    });


  }

  @AfterEach
  void cleanup() {
    adminClient.resource(testCustomResource()).delete();
    adminClient.resource(namespace()).delete();
  }

  @Test
  void startsUpWhenNoPermission() {
    LocallyRunOperatorExtension.applyCrd(RBACBehaviorTestCustomResource.class, adminClient);
    adminClient.resource(testCustomResource()).createOrReplace();
    // todo it should not throw exception
    fullResourcesAccess();

    startOperator();

    assertReconciled();
  }


  @Test
  void resilientForLoosingPermissionForCustomResource() throws InterruptedException {
    LocallyRunOperatorExtension.applyCrd(RBACBehaviorTestCustomResource.class, adminClient);
    fullResourcesAccess();
    startOperator();
    noCustomResourceAccess();

    Thread.sleep(5000);
    adminClient.resource(testCustomResource()).createOrReplace();

    await().pollDelay(Duration.ofMillis(300)).untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isEqualTo(0);
    });

  }

  @Test
  void resilientForLoosingPermissionForSecondaryResource() {

  }

  @Test
  void notStartsUpWithoutPermissionIfInstructed() {

  }

  @Test
  RBACBehaviorTestCustomResource testCustomResource() {
    RBACBehaviorTestCustomResource testCustomResource = new RBACBehaviorTestCustomResource();
    testCustomResource.setMetadata(new ObjectMetaBuilder()
        .withNamespace(actualNamespace)
        .withName("test1")
        .build());
    return testCustomResource;
  }


  private void assertReconciled() {
    await().untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(0);
    });
  }

  KubernetesClient clientUsingServiceAccount() {
    KubernetesClient client = new KubernetesClientBuilder()
        .withConfig(new ConfigBuilder()
            .withImpersonateUsername("rbac-test-user")
            .withNamespace(actualNamespace)
            .build())
        .build();
    return client;
  }

  Operator startOperator() {
    reconciler = new RBACBehaviorTestReconciler();
    Operator operator = new Operator(clientUsingServiceAccount());
    operator.register(reconciler);
    operator.installShutdownHook();
    operator.start();
    return operator;
  }

  private void noCustomResourceAccess() {
    applyClusterRole("rback-test-no-cr-access.yaml");
    applyClusterRoleBinding();
  }

  private void fullResourcesAccess() {
    applyClusterRole("rback-test-full-access-role.yaml");
    applyClusterRoleBinding();
  }

  private void applyClusterRoleBinding() {
    var clusterRoleBinding = ReconcilerUtils
        .loadYaml(ClusterRoleBinding.class, this.getClass(), "rback-test-role-binding.yaml");
    adminClient.resource(clusterRoleBinding).createOrReplace();
  }

  private void applyClusterRole(String filename) {
    var clusterRole = ReconcilerUtils
        .loadYaml(ClusterRole.class, this.getClass(), filename);
    adminClient.resource(clusterRole).createOrReplace();
  }

  private Namespace namespace() {
    Namespace n = new Namespace();
    n.setMetadata(new ObjectMetaBuilder()
        .withName(actualNamespace)
        .build());
    return n;
  }
}
