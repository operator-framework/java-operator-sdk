package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.cleanermanageddependent.CleanerForManagedDependentCustomResource;
import io.javaoperatorsdk.operator.sample.cleanermanageddependent.CleanerForManagedDependentTestReconciler;
import io.javaoperatorsdk.operator.sample.cleanermanageddependent.ConfigMapDependentResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DeleterForManagedDependentResourcesOnlyIT {

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

    await().until(
        () -> !operator.get(CleanerForManagedDependentCustomResource.class, TEST_RESOURCE_NAME)
            .getMetadata().getFinalizers().isEmpty());

    operator.delete(testResource);

    await().until(
        () -> operator.get(CleanerForManagedDependentCustomResource.class,
            TEST_RESOURCE_NAME) == null);

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
