package io.javaoperatorsdk.operator;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.multiplemanageddependent.MultipleManagedDependentResourceCustomResource;
import io.javaoperatorsdk.operator.sample.multiplemanageddependent.MultipleManagedDependentResourceReconciler;
import io.javaoperatorsdk.operator.sample.multiplemanageddependent.MultipleManagedDependentResourceSpec;

import static io.javaoperatorsdk.operator.sample.multiplemanageddependent.MultipleManagedDependentResourceReconciler.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MultipleManagedDependentSameTypeIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String DEFAULT_SPEC_VALUE = "val";
  public static final String UPDATED_SPEC_VALUE = "updated-val";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new MultipleManagedDependentResourceReconciler())
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
    await().untilAsserted(() -> {
      var maps = operator.getKubernetesClient().configMaps()
          .inNamespace(operator.getNamespace()).list().getItems().stream()
          .filter(cm -> cm.getMetadata().getName().startsWith(TEST_RESOURCE_NAME))
          .collect(Collectors.toList());
      assertThat(maps).hasSize(2);
      assertThat(maps).allMatch(cm -> cm.getData().get(DATA_KEY).equals(expectedData));
    });
  }

  private void assertConfigMapsDeleted() {
    await().untilAsserted(() -> {
      var maps = operator.getKubernetesClient().configMaps()
          .inNamespace(operator.getNamespace()).list().getItems().stream()
          .filter(cm -> cm.getMetadata().getName().startsWith(TEST_RESOURCE_NAME))
          .collect(Collectors.toList());
      assertThat(maps).hasSize(0);
    });
  }

  private MultipleManagedDependentResourceCustomResource testResource() {
    var res = new MultipleManagedDependentResourceCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE_NAME)
        .build());

    res.setSpec(new MultipleManagedDependentResourceSpec());
    res.getSpec().setValue(DEFAULT_SPEC_VALUE);
    return res;
  }

}
