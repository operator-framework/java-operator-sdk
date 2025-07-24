package io.javaoperatorsdk.operator.dependent.dependentresourcecrossref;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DependentResourceCrossRefIT {

  public static final String TEST_RESOURCE_NAME = "test";
  public static final int EXECUTION_NUMBER = 250;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new DependentResourceCrossRefReconciler())
          .build();

  @Test
  void dependentResourceCanReferenceEachOther() {

    for (int i = 0; i < EXECUTION_NUMBER; i++) {
      operator.create(testResource(i));
    }
    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(DependentResourceCrossRefReconciler.class)
                          .isErrorHappened())
                  .isFalse();
              for (int i = 0; i < EXECUTION_NUMBER; i++) {
                assertThat(operator.get(ConfigMap.class, TEST_RESOURCE_NAME + i)).isNotNull();
                assertThat(operator.get(Secret.class, TEST_RESOURCE_NAME + i)).isNotNull();
              }
            });

    for (int i = 0; i < EXECUTION_NUMBER; i++) {
      operator.delete(testResource(i));
    }
    await()
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              for (int i = 0; i < EXECUTION_NUMBER; i++) {
                assertThat(
                        operator.get(
                            DependentResourceCrossRefResource.class,
                            testResource(i).getMetadata().getName()))
                    .isNull();
              }
            });
  }

  DependentResourceCrossRefResource testResource(int n) {
    var res = new DependentResourceCrossRefResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME + n).build());
    return res;
  }
}
