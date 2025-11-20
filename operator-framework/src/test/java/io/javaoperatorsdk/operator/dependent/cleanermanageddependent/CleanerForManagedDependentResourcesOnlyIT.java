package io.javaoperatorsdk.operator.dependent.cleanermanageddependent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Cleanup handlers for managed dependent resources",
    description =
        "Shows how to implement cleanup logic for managed dependent resources using the Cleaner"
            + " interface. The framework automatically adds finalizers and invokes the cleanup"
            + " method when the primary resource is deleted, ensuring proper cleanup of dependent"
            + " resources.")
class CleanerForManagedDependentResourcesOnlyIT {

  public static final String TEST_RESOURCE_NAME = "cleaner-for-reconciler-test1";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new CleanerForManagedDependentTestReconciler())
          .build();

  @Test
  void addsFinalizerAndCallsCleanupIfCleanerImplemented() {
    var testResource = createTestResource();
    operator.create(testResource);

    await()
        .until(
            () ->
                !operator
                    .get(CleanerForManagedDependentCustomResource.class, TEST_RESOURCE_NAME)
                    .getMetadata()
                    .getFinalizers()
                    .isEmpty());

    operator.delete(testResource);

    await()
        .until(
            () ->
                operator.get(CleanerForManagedDependentCustomResource.class, TEST_RESOURCE_NAME)
                    == null);

    CleanerForManagedDependentTestReconciler reconciler =
        (CleanerForManagedDependentTestReconciler) operator.getFirstReconciler();

    assertThat(reconciler.getNumberOfExecutions()).isEqualTo(1);
    assertThat(ConfigMapDependentResource.getNumberOfCleanupExecutions()).isEqualTo(1);
  }

  private CleanerForManagedDependentCustomResource createTestResource() {
    CleanerForManagedDependentCustomResource cr = new CleanerForManagedDependentCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(TEST_RESOURCE_NAME);
    return cr;
  }
}
