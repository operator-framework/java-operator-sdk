package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocalOperatorExtension;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;
import io.javaoperatorsdk.operator.sample.multipledependentresource.MultipleDependentResourceConfigMap;
import io.javaoperatorsdk.operator.sample.multipledependentresource.MultipleDependentResourceCustomResource;
import io.javaoperatorsdk.operator.sample.multipledependentresource.MultipleDependentResourceReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class AbstractMultipleDependentResourceIT<ReconcilerClass extends AbstractExecutionNumberRecordingReconciler<MultipleDependentResourceCustomResource>> {

  public static final String TEST_RESOURCE_NAME = "multipledependentresource-testresource";
  @RegisterExtension
  LocalOperatorExtension operator =
      LocalOperatorExtension.builder().withReconciler(getReconcilerClass())
          .waitForNamespaceDeletion(true)
          .build();

  protected abstract Class<ReconcilerClass> getReconcilerClass();

  @Test
  void twoConfigMapsHaveBeenCreated() {
    var customResource = createTestCustomResource();
    operator.create(MultipleDependentResourceCustomResource.class, customResource);

    var reconciler = operator.getReconcilerOfType(getReconcilerClass());

    await().pollDelay(Duration.ofMillis(300))
        .until(() -> reconciler.getNumberOfExecutions() <= 1);

    IntStream.of(MultipleDependentResourceReconciler.FIRST_CONFIG_MAP_ID,
        MultipleDependentResourceReconciler.SECOND_CONFIG_MAP_ID).forEach(configMapId -> {
          ConfigMap configMap =
              operator.get(ConfigMap.class, customResource.getConfigMapName(configMapId));
          assertThat(configMap).isNotNull();
          assertThat(configMap.getMetadata().getName())
              .isEqualTo(customResource.getConfigMapName(configMapId));
          assertThat(configMap.getData().get(MultipleDependentResourceConfigMap.DATA_KEY))
              .isEqualTo(String.valueOf(configMapId));
        });
  }

  public MultipleDependentResourceCustomResource createTestCustomResource() {
    var resource = new MultipleDependentResourceCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST_RESOURCE_NAME)
            .withNamespace(operator.getNamespace())
            .build());
    return resource;
  }
}
