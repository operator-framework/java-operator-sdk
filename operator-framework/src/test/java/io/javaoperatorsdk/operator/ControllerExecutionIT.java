package io.javaoperatorsdk.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceController;
import io.javaoperatorsdk.operator.support.TestUtils;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ControllerExecutionIT {
  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withController(new TestCustomResourceController(true))
          .build();

  @Test
  public void configMapGetsCreatedForTestCustomResource() {
    operator.getControllerOfType(TestCustomResourceController.class).setUpdateStatus(true);

    TestCustomResource resource = TestUtils.testCustomResource();
    operator.resources(TestCustomResource.class).create(resource);

    awaitResourcesCreatedOrUpdated();
    awaitStatusUpdated();
    assertThat(TestUtils.getNumberOfExecutions(operator)).isEqualTo(2);
  }

  @Test
  public void eventIsSkippedChangedOnMetadataOnlyUpdate() {
    operator.getControllerOfType(TestCustomResourceController.class).setUpdateStatus(false);

    TestCustomResource resource = TestUtils.testCustomResource();
    operator.resources(TestCustomResource.class).create(resource);

    awaitResourcesCreatedOrUpdated();
    assertThat(TestUtils.getNumberOfExecutions(operator)).isEqualTo(1);
  }

  void awaitResourcesCreatedOrUpdated() {
    await("config map created")
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              ConfigMap configMap =
                  operator.getNamedResource(ConfigMap.class, "test-config-map");
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
                  operator.getNamedResource(TestCustomResource.class,
                      TestUtils.TEST_CUSTOM_RESOURCE_NAME);
              assertThat(cr).isNotNull();
              assertThat(cr.getStatus()).isNotNull();
              assertThat(cr.getStatus().getConfigMapStatus()).isEqualTo("ConfigMap Ready");
            });
  }
}
