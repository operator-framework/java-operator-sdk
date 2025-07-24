package io.javaoperatorsdk.operator.dependent.kubernetesdependentgarbagecollection;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.IntegrationTestConstants;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class KubernetesDependentGarbageCollectionIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  static LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new DependentGarbageCollectionTestReconciler())
          .build();

  @Test
  void resourceSecondaryResourceIsGarbageCollected() {
    var resource = customResource();
    var createdResources = operator.create(resource);

    await()
        .untilAsserted(
            () -> {
              ConfigMap configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(configMap).isNotNull();
            });

    ConfigMap configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
    assertThat(configMap.getMetadata().getOwnerReferences()).hasSize(1);
    assertThat(configMap.getMetadata().getOwnerReferences().get(0).getName())
        .isEqualTo(TEST_RESOURCE_NAME);

    operator.delete(createdResources);

    await()
        .atMost(Duration.ofSeconds(IntegrationTestConstants.GARBAGE_COLLECTION_TIMEOUT_SECONDS))
        .untilAsserted(
            () -> {
              ConfigMap cm = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(cm).isNull();
            });
  }

  @Test
  void deletesSecondaryResource() {
    var resource = customResource();
    var createdResources = operator.create(resource);

    await()
        .untilAsserted(
            () -> {
              ConfigMap configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(configMap).isNotNull();
            });

    createdResources.getSpec().setCreateConfigMap(false);
    operator.replace(createdResources);

    await()
        .untilAsserted(
            () -> {
              ConfigMap cm = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(cm).isNull();
            });
  }

  DependentGarbageCollectionTestCustomResource customResource() {
    DependentGarbageCollectionTestCustomResource resource =
        new DependentGarbageCollectionTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    resource.setSpec(new DependentGarbageCollectionTestCustomResourceSpec());
    resource.getSpec().setCreateConfigMap(true);
    return resource;
  }
}
