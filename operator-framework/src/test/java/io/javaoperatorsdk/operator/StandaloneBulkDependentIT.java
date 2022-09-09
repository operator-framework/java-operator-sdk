package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.standalonebulkdependent.StandaloneBulkDependentReconciler;
import io.javaoperatorsdk.operator.sample.standalonebulkdependent.StandaloneBulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.sample.standalonebulkdependent.StandaloneBulkDependentTestSpec;

import static io.javaoperatorsdk.operator.sample.standalonebulkdependent.ConfigMapBulkDependentResource.LABEL_KEY;
import static io.javaoperatorsdk.operator.sample.standalonebulkdependent.ConfigMapBulkDependentResource.LABEL_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class StandaloneBulkDependentIT {

  public static final String TEST_RESOURCE_NAME = "test";
  public static final int NUMBER_OF_CONFIG_MAPS = 3;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new StandaloneBulkDependentReconciler())
          .build();

  @Test
  void managesBulkConfigMaps() {
    operator.create(testResource());

    await().untilAsserted(() -> {
      var cms = operator.getKubernetesClient().configMaps().inNamespace(operator.getNamespace())
          .withLabel(LABEL_KEY, LABEL_VALUE)
          .list().getItems();
      assertThat(cms).hasSize(NUMBER_OF_CONFIG_MAPS);
    });
  }

  private StandaloneBulkDependentTestCustomResource testResource() {
    StandaloneBulkDependentTestCustomResource cr = new StandaloneBulkDependentTestCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(TEST_RESOURCE_NAME);
    cr.setSpec(new StandaloneBulkDependentTestSpec());
    cr.getSpec().setNumberOfResources(NUMBER_OF_CONFIG_MAPS);
    return cr;
  }

}
