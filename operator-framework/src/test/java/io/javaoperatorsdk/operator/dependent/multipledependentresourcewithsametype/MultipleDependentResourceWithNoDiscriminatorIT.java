package io.javaoperatorsdk.operator.dependent.multipledependentresourcewithsametype;

import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MultipleDependentResourceWithNoDiscriminatorIT {

  public static final String TEST_RESOURCE_NAME = "multipledependentresource-testresource";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(MultipleDependentResourceWithDiscriminatorReconciler.class)
          .waitForNamespaceDeletion(true)
          .build();

  @Test
  void twoConfigMapsHaveBeenCreated() {
    MultipleDependentResourceCustomResourceNoDiscriminator customResource =
        createTestCustomResource();
    operator.create(customResource);

    var reconciler =
        operator.getReconcilerOfType(MultipleDependentResourceWithDiscriminatorReconciler.class);

    await().pollDelay(Duration.ofMillis(300)).until(() -> reconciler.getNumberOfExecutions() <= 1);

    IntStream.of(
            MultipleDependentResourceWithDiscriminatorReconciler.FIRST_CONFIG_MAP_ID,
            MultipleDependentResourceWithDiscriminatorReconciler.SECOND_CONFIG_MAP_ID)
        .forEach(
            configMapId -> {
              ConfigMap configMap =
                  operator.get(ConfigMap.class, customResource.getConfigMapName(configMapId));
              assertThat(configMap).isNotNull();
              assertThat(configMap.getMetadata().getName())
                  .isEqualTo(customResource.getConfigMapName(configMapId));
              assertThat(configMap.getData().get(MultipleDependentResourceConfigMap.DATA_KEY))
                  .isEqualTo(String.valueOf(configMapId));
            });
  }

  public MultipleDependentResourceCustomResourceNoDiscriminator createTestCustomResource() {
    MultipleDependentResourceCustomResourceNoDiscriminator resource =
        new MultipleDependentResourceCustomResourceNoDiscriminator();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST_RESOURCE_NAME)
            .withNamespace(operator.getNamespace())
            .build());
    return resource;
  }
}
