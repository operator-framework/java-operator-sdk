package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.bulkdependent.StandaloneBulkDependentReconciler;
import io.javaoperatorsdk.operator.sample.bulkdependent.StandaloneBulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.sample.bulkdependent.StandaloneBulkDependentTestSpec;

import static io.javaoperatorsdk.operator.sample.bulkdependent.ConfigMapBulkDependentResource.LABEL_KEY;
import static io.javaoperatorsdk.operator.sample.bulkdependent.ConfigMapBulkDependentResource.LABEL_VALUE;
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
    assertNumberOfConfigMaps(3);

    updateSpecWithNumber(1);
    assertNumberOfConfigMaps(1);

    updateSpecWithNumber(5);
    assertNumberOfConfigMaps(5);

    operator.delete(testResource());
    assertNumberOfConfigMaps(0);
  }

  void assertNumberOfConfigMaps(int n) {
    // this test was failing with a lower timeout on GitHub, probably the garbage collection was
    // slower there.
    await().atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> {
          var cms = operator.getKubernetesClient().configMaps().inNamespace(operator.getNamespace())
              .withLabel(LABEL_KEY, LABEL_VALUE)
              .list().getItems();
          assertThat(cms).withFailMessage("Number of items is still: " + cms.size())
              .hasSize(n);
        });
  }

  private void updateSpecWithNumber(int n) {
    var resource = testResource();
    resource.getSpec().setNumberOfResources(n);
    operator.replace(resource);
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
