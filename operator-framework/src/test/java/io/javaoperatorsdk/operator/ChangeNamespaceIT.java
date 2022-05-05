package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.changenamespace.ChangeNamespaceTestCustomResource;
import io.javaoperatorsdk.operator.sample.changenamespace.ChangeNamespaceTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ChangeNamespaceIT {

  public static final String TEST_RESOURCE_NAME_1 = "test1";
  public static final String TEST_RESOURCE_NAME_2 = "test2";
  public static final String TEST_RESOURCE_NAME_3 = "test3";
  public static final String ADDITIONAL_TEST_NAMESPACE = "additional-test-namespace";
  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder().withReconciler(new ChangeNamespaceTestReconciler()).build();

  @Test
  void addNewAndRemoveOldNamespaceTest() {
    try {
      operator.create(ChangeNamespaceTestCustomResource.class,
          customResource(TEST_RESOURCE_NAME_1));

      await().pollDelay(Duration.ofMillis(100)).untilAsserted(() -> assertThat(
          operator.get(ChangeNamespaceTestCustomResource.class, TEST_RESOURCE_NAME_1)
              .getStatus().getNumberOfStatusUpdates()).isEqualTo(2));

      client().namespaces().create(additionalTestNamespace());
      createResourceInTestNamespace();

      await().pollDelay(Duration.ofMillis(200)).untilAsserted(
          () -> assertThat(client().resources(ChangeNamespaceTestCustomResource.class)
              .inNamespace(ADDITIONAL_TEST_NAMESPACE)
              .withName(TEST_RESOURCE_NAME_2).get().getStatus().getNumberOfStatusUpdates())
                  .isZero());

      // adding additional namespace
      RegisteredController registeredController =
          operator.getRegisteredControllerForReconcile(ChangeNamespaceTestReconciler.class);
      registeredController
          .changeNamespaces(Set.of(operator.getNamespace(), ADDITIONAL_TEST_NAMESPACE));

      await().untilAsserted(
          () -> assertThat(client().resources(ChangeNamespaceTestCustomResource.class)
              .inNamespace(ADDITIONAL_TEST_NAMESPACE)
              .withName(TEST_RESOURCE_NAME_2).get().getStatus().getNumberOfStatusUpdates())
                  .withFailMessage("Informers don't watch the new namespace")
                  .isEqualTo(2));

      // removing a namespace
      registeredController.changeNamespaces(Set.of(ADDITIONAL_TEST_NAMESPACE));

      operator.create(ChangeNamespaceTestCustomResource.class,
          customResource(TEST_RESOURCE_NAME_3));
      await().pollDelay(Duration.ofMillis(200))
          .untilAsserted(() -> assertThat(
              operator.get(ChangeNamespaceTestCustomResource.class, TEST_RESOURCE_NAME_3)
                  .getStatus().getNumberOfStatusUpdates()).isZero());


      ConfigMap firstMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME_1);
      firstMap.setData(Map.of("data", "newdata"));
      operator.replace(ConfigMap.class, firstMap);

      assertThat(operator.get(ChangeNamespaceTestCustomResource.class, TEST_RESOURCE_NAME_1)
          .getStatus().getNumberOfStatusUpdates())
              .withFailMessage("ConfigMap change induced a reconcile")
              .isEqualTo(2);

    } finally {
      client().namespaces().delete(additionalTestNamespace());
    }
  }

  private void createResourceInTestNamespace() {
    var res = customResource(TEST_RESOURCE_NAME_2);
    client().resources(ChangeNamespaceTestCustomResource.class)
        .inNamespace(ADDITIONAL_TEST_NAMESPACE)
        .create(res);
  }

  private KubernetesClient client() {
    return operator.getKubernetesClient();
  }

  private Namespace additionalTestNamespace() {
    return new NamespaceBuilder().withMetadata(new ObjectMetaBuilder()
        .withName(ADDITIONAL_TEST_NAMESPACE)
        .build()).build();
  }

  private ChangeNamespaceTestCustomResource customResource(String name) {
    ChangeNamespaceTestCustomResource customResource = new ChangeNamespaceTestCustomResource();
    customResource.setMetadata(
        new ObjectMetaBuilder().withName(name).build());
    return customResource;
  }
}
