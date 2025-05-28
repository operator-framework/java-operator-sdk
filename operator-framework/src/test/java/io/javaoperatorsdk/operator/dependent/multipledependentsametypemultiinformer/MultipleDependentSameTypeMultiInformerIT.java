package io.javaoperatorsdk.operator.dependent.multipledependentsametypemultiinformer;

import java.time.Duration;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.IntegrationTestConstants.GARBAGE_COLLECTION_TIMEOUT_SECONDS;
import static io.javaoperatorsdk.operator.dependent.multipledependentsametypemultiinformer.MultipleManagedDependentResourceMultiInformerReconciler.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MultipleDependentSameTypeMultiInformerIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String DEFAULT_SPEC_VALUE = "val";
  public static final String UPDATED_SPEC_VALUE = "updated-val";
  public static final int SECONDS = 30;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new MultipleManagedDependentResourceMultiInformerReconciler())
          .build();

  @Test
  void handlesCrudOperations() {
    operator.create(testResource());
    assertConfigMapsPresent(DEFAULT_SPEC_VALUE);

    var updatedResource = testResource();
    updatedResource.getSpec().setValue(UPDATED_SPEC_VALUE);
    operator.replace(updatedResource);
    assertConfigMapsPresent(UPDATED_SPEC_VALUE);

    operator.delete(testResource());
    assertConfigMapsDeleted();
  }

  private void assertConfigMapsPresent(String expectedData) {
    await()
        .untilAsserted(
            () -> {
              var maps =
                  operator
                      .getKubernetesClient()
                      .configMaps()
                      .inNamespace(operator.getNamespace())
                      .list()
                      .getItems()
                      .stream()
                      .filter(cm -> cm.getMetadata().getName().startsWith(TEST_RESOURCE_NAME))
                      .collect(Collectors.toList());
              assertThat(maps).hasSize(2);
              assertThat(maps).allMatch(cm -> cm.getData().get(DATA_KEY).equals(expectedData));
            });
  }

  private void assertConfigMapsDeleted() {
    await()
        .atMost(Duration.ofSeconds(GARBAGE_COLLECTION_TIMEOUT_SECONDS))
        .untilAsserted(
            () -> {
              var maps =
                  operator
                      .getKubernetesClient()
                      .configMaps()
                      .inNamespace(operator.getNamespace())
                      .list()
                      .getItems()
                      .stream()
                      .filter(cm -> cm.getMetadata().getName().startsWith(TEST_RESOURCE_NAME))
                      .collect(Collectors.toList());
              assertThat(maps).hasSize(0);
            });
  }

  private MultipleManagedDependentResourceMultiInformerCustomResource testResource() {
    var res = new MultipleManagedDependentResourceMultiInformerCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());

    res.setSpec(new MultipleManagedDependentResourceMultiInformerSpec());
    res.getSpec().setValue(DEFAULT_SPEC_VALUE);
    return res;
  }
}
