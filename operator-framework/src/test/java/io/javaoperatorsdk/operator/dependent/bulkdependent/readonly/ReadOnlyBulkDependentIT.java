package io.javaoperatorsdk.operator.dependent.bulkdependent.readonly;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestSpec;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ReadOnlyBulkDependentIT {

  public static final int EXPECTED_NUMBER_OF_RESOURCES = 2;
  public static final String TEST = "test";

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new ReadOnlyBulkReconciler()).build();

  @Test
  void readOnlyBulkDependent() {
    var primary = extension.create(testCustomResource());

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              var actualPrimary = extension.get(BulkDependentTestCustomResource.class, TEST);

              assertThat(actualPrimary.getStatus()).isNotNull();
              assertThat(actualPrimary.getStatus().getReady()).isFalse();
            });

    var configMap1 = createConfigMap(1, primary);
    extension.create(configMap1);
    var configMap2 = createConfigMap(2, primary);
    extension.create(configMap2);

    await()
        .untilAsserted(
            () -> {
              var actualPrimary = extension.get(BulkDependentTestCustomResource.class, TEST);
              assertThat(actualPrimary.getStatus().getReady()).isTrue();
            });
  }

  private ConfigMap createConfigMap(int i, BulkDependentTestCustomResource primary) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST + ReadOnlyBulkDependentResource.INDEX_DELIMITER + i)
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    configMap.addOwnerReference(primary);
    return configMap;
  }

  BulkDependentTestCustomResource testCustomResource() {
    BulkDependentTestCustomResource customResource = new BulkDependentTestCustomResource();
    customResource.setMetadata(new ObjectMetaBuilder().withName(TEST).build());
    customResource.setSpec(new BulkDependentTestSpec());
    customResource.getSpec().setNumberOfResources(EXPECTED_NUMBER_OF_RESOURCES);

    return customResource;
  }
}
