package io.javaoperatorsdk.operator.dependent.bulkdependent.external;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestBase.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BulkExternalDependentIT {

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ExternalBulkResourceReconciler())
          .build();

  ExternalServiceMock externalServiceMock = ExternalServiceMock.getInstance();

  @Test
  void managesExternalBulkResources() {
    extension.create(testResource());
    assertResourceNumberAndData(3, INITIAL_ADDITIONAL_DATA);

    updateSpecWithNumber(extension, 1);
    assertResourceNumberAndData(1, INITIAL_ADDITIONAL_DATA);

    updateSpecWithNumber(extension, 5);
    assertResourceNumberAndData(5, INITIAL_ADDITIONAL_DATA);

    extension.delete(testResource());
    assertResourceNumberAndData(0, INITIAL_ADDITIONAL_DATA);
  }

  @Test
  void handlesResourceUpdates() {
    extension.create(testResource());
    assertResourceNumberAndData(3, INITIAL_ADDITIONAL_DATA);

    updateSpecWithNewAdditionalData(extension, NEW_VERSION_OF_ADDITIONAL_DATA);
    assertResourceNumberAndData(3, NEW_VERSION_OF_ADDITIONAL_DATA);
  }

  private void assertResourceNumberAndData(int n, String data) {
    await()
        .untilAsserted(
            () -> {
              var resources = externalServiceMock.listResources();
              assertThat(resources).hasSize(n);
              assertThat(resources).allMatch(r -> r.getData().equals(data));
            });
  }
}
