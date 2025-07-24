package io.javaoperatorsdk.operator.dependent.bulkdependent.condition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestBase.INITIAL_NUMBER_OF_CONFIG_MAPS;
import static io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestBase.testResource;
import static io.javaoperatorsdk.operator.dependent.bulkdependent.ConfigMapDeleterBulkDependentResource.LABEL_KEY;
import static io.javaoperatorsdk.operator.dependent.bulkdependent.ConfigMapDeleterBulkDependentResource.LABEL_VALUE;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

class BulkDependentWithConditionIT {

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ManagedBulkDependentWithReadyConditionReconciler())
          .build();

  @Test
  void handlesBulkDependentWithPrecondition() {
    var resource = testResource();
    extension.create(resource);

    await()
        .untilAsserted(
            () -> {
              var res =
                  extension.get(
                      BulkDependentTestCustomResource.class,
                      testResource().getMetadata().getName());
              assertThat(res.getStatus()).isNotNull();
              assertThat(res.getStatus().getReady()).isTrue();

              var cms =
                  extension
                      .getKubernetesClient()
                      .configMaps()
                      .inNamespace(extension.getNamespace())
                      .withLabel(LABEL_KEY, LABEL_VALUE)
                      .list()
                      .getItems();
              assertThat(cms).hasSize(INITIAL_NUMBER_OF_CONFIG_MAPS);
            });
  }
}
