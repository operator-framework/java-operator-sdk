package io.javaoperatorsdk.operator.baseapi.updatestatusincleanupandreschedule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class UpdateStatusInCleanupAndRescheduleIT {

  public static final String TEST_RESOURCE = "test1";

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(UpdateStatusInCleanupAndRescheduleReconciler.class)
          .build();

  @Test
  void testRescheduleAfterPatch() {
    var res = extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var resource =
                  extension.get(
                      UpdateStatusInCleanupAndRescheduleCustomResource.class, TEST_RESOURCE);
              assertThat(resource.getMetadata().getFinalizers()).isNotEmpty();
            });

    extension.delete(res);

    await()
        .untilAsserted(
            () -> {
              var resource =
                  extension.get(
                      UpdateStatusInCleanupAndRescheduleCustomResource.class, TEST_RESOURCE);
              assertThat(resource).isNull();
            });

    assertThat(
            extension
                .getReconcilerOfType(UpdateStatusInCleanupAndRescheduleReconciler.class)
                .getRescheduleDelayWorked())
        .isTrue();
  }

  UpdateStatusInCleanupAndRescheduleCustomResource testResource() {
    var resource = new UpdateStatusInCleanupAndRescheduleCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE).build());
    return resource;
  }
}
