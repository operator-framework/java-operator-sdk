package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestReconciler;
import io.javaoperatorsdk.operator.support.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ReconcilerExecutorIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new TestReconciler(true)).build();

  @Test
  void configMapGetsCreatedForTestCustomResource() {
    operator.getReconcilerOfType(TestReconciler.class).setUpdateStatus(true);

    TestCustomResource resource = TestUtils.testCustomResource();
    operator.create(resource);

    awaitResourcesCreatedOrUpdated();
    awaitStatusUpdated();
    assertThat(TestUtils.getNumberOfExecutions(operator)).isEqualTo(2);
  }

  @Test
  void patchesStatusForTestCustomResource() {
    operator.getReconcilerOfType(TestReconciler.class).setPatchStatus(true);
    operator.getReconcilerOfType(TestReconciler.class).setUpdateStatus(true);

    TestCustomResource resource = TestUtils.testCustomResource();
    operator.create(resource);

    awaitStatusUpdated();
  }

  @Test
  void eventIsSkippedChangedOnMetadataOnlyUpdate() {
    operator.getReconcilerOfType(TestReconciler.class).setUpdateStatus(false);

    TestCustomResource resource = TestUtils.testCustomResource();
    operator.create(resource);

    awaitResourcesCreatedOrUpdated();
    assertThat(TestUtils.getNumberOfExecutions(operator)).isEqualTo(1);
  }

  @Test
  void cleanupExecuted() {
    operator.getReconcilerOfType(TestReconciler.class).setUpdateStatus(true);

    TestCustomResource resource = TestUtils.testCustomResource();
    resource = operator.create(resource);

    awaitResourcesCreatedOrUpdated();
    awaitStatusUpdated();
    operator.delete(resource);

    await().atMost(Duration.ofSeconds(1))
        .until(() -> ((TestReconciler) operator.getFirstReconciler())
            .getNumberOfCleanupExecutions() == 1);
  }

  void awaitResourcesCreatedOrUpdated() {
    await("config map created")
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              ConfigMap configMap =
                  operator.get(ConfigMap.class, "test-config-map");
              assertThat(configMap).isNotNull();
              assertThat(configMap.getData().get("test-key")).isEqualTo("test-value");
            });
  }

  void awaitStatusUpdated() {
    awaitStatusUpdated(5);
  }

  void awaitStatusUpdated(int timeout) {
    await("cr status updated")
        .atMost(timeout, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              TestCustomResource cr =
                  operator.get(TestCustomResource.class,
                      TestUtils.TEST_CUSTOM_RESOURCE_NAME);
              assertThat(cr).isNotNull();
              assertThat(cr.getStatus()).isNotNull();
              assertThat(cr.getStatus().getConfigMapStatus()).isEqualTo("ConfigMap Ready");
            });
  }
}
