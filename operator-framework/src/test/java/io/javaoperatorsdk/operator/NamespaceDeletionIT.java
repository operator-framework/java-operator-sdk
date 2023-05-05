package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.namespacedeletion.NamespaceDeletionTestCustomResource;
import io.javaoperatorsdk.operator.sample.namespacedeletion.NamespaceDeletionTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class NamespaceDeletionIT {

  KubernetesClient adminClient = new KubernetesClientBuilder().build();

  KubernetesClient client = new KubernetesClientBuilder()
      .withConfig(new ConfigBuilder()
          .withImpersonateUsername("namespace-deletion-test-user")
          .build())
      .build();

  String actualNamespace;
  Operator operator;

  @BeforeEach
  void beforeEach(TestInfo testInfo) {
    LocallyRunOperatorExtension.applyCrd(NamespaceDeletionTestCustomResource.class,
        adminClient);

    testInfo.getTestMethod().ifPresent(method -> {
      actualNamespace = KubernetesResourceUtil.sanitizeName(method.getName());
      adminClient.resource(namespace()).create();
    });

    applyRBACResources();
    operator = new Operator(client);
    operator.register(new NamespaceDeletionTestReconciler(),
        o -> o.settingNamespaces(actualNamespace));
    operator.start();
  }

  @AfterEach
  void cleanup() {
    if (operator != null) {
      operator.stop(Duration.ofSeconds(1));
    }
  }

  @Test
  void testDeletingNamespaceWithRolesForOperator() {
    var res = adminClient.resource(testResource()).create();

    await().untilAsserted(() -> {
      var actual = adminClient.resource(res).get();
      assertThat(actual.getMetadata().getFinalizers()).isNotEmpty();
    });

    adminClient.resource(namespace()).delete();

    await().untilAsserted(() -> {
      var actual = adminClient.resource(res).get();
      assertThat(actual).isNull();
    });
  }

  NamespaceDeletionTestCustomResource testResource() {
    NamespaceDeletionTestCustomResource resource = new NamespaceDeletionTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder()
        .withName("test1")
        .withNamespace(actualNamespace)
        .build());
    return resource;
  }

  private Namespace namespace() {
    return namespace(actualNamespace);
  }

  private Namespace namespace(String name) {
    Namespace n = new Namespace();
    n.setMetadata(new ObjectMetaBuilder()
        .withName(name)
        .withName(actualNamespace)
        .build());
    return n;
  }

  private void applyRBACResources() {
    var role = ReconcilerUtils
        .loadYaml(Role.class, NamespaceDeletionTestReconciler.class, "role.yaml");
    role.getMetadata().setNamespace(actualNamespace);
    adminClient.resource(role).create();

    var roleBinding = ReconcilerUtils
        .loadYaml(RoleBinding.class, NamespaceDeletionTestReconciler.class, "role-binding.yaml");
    roleBinding.getMetadata().setNamespace(actualNamespace);
    adminClient.resource(roleBinding).create();
  }
}
