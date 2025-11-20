package io.javaoperatorsdk.operator.baseapi.clusterscopedresource;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.IntegrationTestConstants.GARBAGE_COLLECTION_TIMEOUT_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Cluster-scoped resource reconciliation",
    description =
        "Demonstrates how to reconcile cluster-scoped custom resources (non-namespaced). This"
            + " test shows CRUD operations on cluster-scoped resources and verifies that"
            + " dependent resources are created, updated, and properly cleaned up when the"
            + " primary resource is deleted.")
class ClusterScopedResourceIT {

  public static final String TEST_NAME = "test1";
  public static final String INITIAL_DATA = "initialData";
  public static final String UPDATED_DATA = "updatedData";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ClusterScopedCustomResourceReconciler())
          .build();

  @Test
  void crudOperationOnClusterScopedCustomResource() {
    var resource = operator.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var res = operator.get(ClusterScopedCustomResource.class, TEST_NAME);
              assertThat(res.getStatus()).isNotNull();
              assertThat(res.getStatus().getCreated()).isTrue();
              var cm = operator.get(ConfigMap.class, TEST_NAME);
              assertThat(cm).isNotNull();
              assertThat(cm.getData().get(ClusterScopedCustomResourceReconciler.DATA_KEY))
                  .isEqualTo(INITIAL_DATA);
            });

    resource.getSpec().setData(UPDATED_DATA);
    operator.replace(resource);
    await()
        .untilAsserted(
            () -> {
              var cm = operator.get(ConfigMap.class, TEST_NAME);
              assertThat(cm).isNotNull();
              assertThat(cm.getData().get(ClusterScopedCustomResourceReconciler.DATA_KEY))
                  .isEqualTo(UPDATED_DATA);
            });

    operator.delete(resource);
    await()
        .atMost(Duration.ofSeconds(GARBAGE_COLLECTION_TIMEOUT_SECONDS))
        .untilAsserted(() -> assertThat(operator.get(ConfigMap.class, TEST_NAME)).isNull());
  }

  ClusterScopedCustomResource testResource() {
    var res = new ClusterScopedCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_NAME).build());
    res.setSpec(new ClusterScopedCustomResourceSpec());
    res.getSpec().setTargetNamespace(operator.getNamespace());
    res.getSpec().setData(INITIAL_DATA);

    return res;
  }
}
