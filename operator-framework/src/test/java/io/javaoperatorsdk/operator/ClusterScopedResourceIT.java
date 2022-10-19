package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.clusterscopedresource.ClusterScopedCustomResource;
import io.javaoperatorsdk.operator.sample.clusterscopedresource.ClusterScopedCustomResourceReconciler;
import io.javaoperatorsdk.operator.sample.clusterscopedresource.ClusterScopedCustomResourceSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ClusterScopedResourceIT {

  public static final String TEST_NAME = "test1";
  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ClusterScopedCustomResourceReconciler()).build();

  @Test
  void crudOperationOnClusterScopedCustomResource() {
    var resource = operator.create(testResource());

    await().untilAsserted(() -> {
      var res = operator.get(ClusterScopedCustomResource.class, TEST_NAME);
      assertThat(operator.getReconcilerOfType(ClusterScopedCustomResourceReconciler.class)
          .getNumberOfExecutions()).isGreaterThan(0);
      assertThat(res.getStatus()).isNotNull();
      assertThat(res.getStatus().getCreated()).isTrue();
      var cm = operator.get(ConfigMap.class, TEST_NAME);
      assertThat(cm).isNotNull();
    });

    operator.delete(resource);

    await().untilAsserted(() -> assertThat(operator.get(ConfigMap.class, TEST_NAME)).isNull());
  }


  ClusterScopedCustomResource testResource() {
    var res = new ClusterScopedCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_NAME)
        .build());
    res.setSpec(new ClusterScopedCustomResourceSpec());
    res.getSpec().setTargetNamespace(operator.getNamespace());

    return res;
  }

}
