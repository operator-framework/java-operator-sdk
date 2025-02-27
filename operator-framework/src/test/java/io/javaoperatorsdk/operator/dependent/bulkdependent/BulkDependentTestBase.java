package io.javaoperatorsdk.operator.dependent.bulkdependent;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.bulkdependent.ConfigMapDeleterBulkDependentResource.LABEL_KEY;
import static io.javaoperatorsdk.operator.dependent.bulkdependent.ConfigMapDeleterBulkDependentResource.LABEL_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class BulkDependentTestBase {

  public static final String TEST_RESOURCE_NAME = "test";
  public static final int INITIAL_NUMBER_OF_CONFIG_MAPS = 3;
  public static final String INITIAL_ADDITIONAL_DATA = "initialData";
  public static final String NEW_VERSION_OF_ADDITIONAL_DATA = "newVersionOfAdditionalData";

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

  @Test
  public void updatesData() {
    extension().create(testResource());
    assertNumberOfConfigMaps(3);
    assertAdditionalDataOnConfigMaps(INITIAL_ADDITIONAL_DATA);

    updateSpecWithNewAdditionalData(NEW_VERSION_OF_ADDITIONAL_DATA);
    assertAdditionalDataOnConfigMaps(NEW_VERSION_OF_ADDITIONAL_DATA);
  }

  private void assertNumberOfConfigMaps(int n) {
    // this test was failing with a lower timeout on GitHub, probably the garbage collection was
    // slower there.
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              var cms =
                  extension()
                      .getKubernetesClient()
                      .configMaps()
                      .inNamespace(extension().getNamespace())
                      .withLabel(LABEL_KEY, LABEL_VALUE)
                      .list()
                      .getItems();
              assertThat(cms).withFailMessage("Number of items is still: " + cms.size()).hasSize(n);
            });
  }

  private void assertAdditionalDataOnConfigMaps(String expectedValue) {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              var cms =
                  extension()
                      .getKubernetesClient()
                      .configMaps()
                      .inNamespace(extension().getNamespace())
                      .withLabel(LABEL_KEY, LABEL_VALUE)
                      .list()
                      .getItems();
              cms.forEach(
                  cm -> {
                    assertThat(
                            cm.getData()
                                .get(ConfigMapDeleterBulkDependentResource.ADDITIONAL_DATA_KEY))
                        .isEqualTo(expectedValue);
                  });
            });
  }

  public static BulkDependentTestCustomResource testResource() {
    BulkDependentTestCustomResource cr = new BulkDependentTestCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(TEST_RESOURCE_NAME);
    cr.setSpec(new BulkDependentTestSpec());
    cr.getSpec().setNumberOfResources(INITIAL_NUMBER_OF_CONFIG_MAPS);
    cr.getSpec().setAdditionalData(INITIAL_ADDITIONAL_DATA);
    return cr;
  }

  private void updateSpecWithNewAdditionalData(String data) {
    var resource = testResource();
    resource.getSpec().setAdditionalData(data);
    extension().replace(resource);
  }

  public static void updateSpecWithNewAdditionalData(
      LocallyRunOperatorExtension extension, String data) {
    var resource = testResource();
    resource.getSpec().setAdditionalData(data);
    extension.replace(resource);
  }

  private void updateSpecWithNumber(int n) {
    var resource = testResource();
    resource.getSpec().setNumberOfResources(n);
    extension().replace(resource);
  }

  public static void updateSpecWithNumber(LocallyRunOperatorExtension extension, int n) {
    var resource = testResource();
    resource.getSpec().setNumberOfResources(n);
    extension.replace(resource);
  }

  public abstract LocallyRunOperatorExtension extension();
}
