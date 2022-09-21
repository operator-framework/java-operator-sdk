package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.sample.bulkdependent.BulkDependentTestSpec;

import static io.javaoperatorsdk.operator.sample.bulkdependent.ConfigMapBulkDependentResource.LABEL_KEY;
import static io.javaoperatorsdk.operator.sample.bulkdependent.ConfigMapBulkDependentResource.LABEL_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class BulkDependentTestBase {

  public static final String TEST_RESOURCE_NAME = "test";
  public static final int INITIAL_NUMBER_OF_CONFIG_MAPS = 3;

  @Test
  public void managesBulkConfigMaps() {
    extension().create(testResource());
    assertNumberOfConfigMaps(3);

    updateSpecWithNumber(1);
    assertNumberOfConfigMaps(1);

    updateSpecWithNumber(5);
    assertNumberOfConfigMaps(5);

    extension().delete(testResource());
    assertNumberOfConfigMaps(0);
  }

  private void assertNumberOfConfigMaps(int n) {
    // this test was failing with a lower timeout on GitHub, probably the garbage collection was
    // slower there.
    await().atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> {
          var cms =
              extension().getKubernetesClient().configMaps().inNamespace(extension().getNamespace())
                  .withLabel(LABEL_KEY, LABEL_VALUE)
                  .list().getItems();
          assertThat(cms).withFailMessage("Number of items is still: " + cms.size())
              .hasSize(n);
        });
  }

  private BulkDependentTestCustomResource testResource() {
    BulkDependentTestCustomResource cr = new BulkDependentTestCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(TEST_RESOURCE_NAME);
    cr.setSpec(new BulkDependentTestSpec());
    cr.getSpec().setNumberOfResources(INITIAL_NUMBER_OF_CONFIG_MAPS);
    return cr;
  }


  private void updateSpecWithNumber(int n) {
    var resource = testResource();
    resource.getSpec().setNumberOfResources(n);
    extension().replace(resource);
  }

  abstract LocallyRunOperatorExtension extension();
}
