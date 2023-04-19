package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.jenvtest.junit.EnableKubeAPIServer;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.unmodifiabledependentpart.UnmodifiableDependentPartCustomResource;
import io.javaoperatorsdk.operator.sample.unmodifiabledependentpart.UnmodifiableDependentPartReconciler;
import io.javaoperatorsdk.operator.sample.unmodifiabledependentpart.UnmodifiableDependentPartSpec;

import static io.javaoperatorsdk.operator.sample.unmodifiabledependentpart.UnmodifiablePartConfigMapDependent.ACTUAL_DATA_KEY;
import static io.javaoperatorsdk.operator.sample.unmodifiabledependentpart.UnmodifiablePartConfigMapDependent.UNMODIFIABLE_INITIAL_DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnableKubeAPIServer
public class UnmodifiableDependentPartIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String INITIAL_DATA = "initialData";
  public static final String UPDATED_DATA = "updatedData";

  static KubernetesClient client;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withKubernetesClient(client)
          .waitForNamespaceDeletion(false)
          .withReconciler(UnmodifiableDependentPartReconciler.class)
          .build();

  @Test
  void partConfigMapDataUnmodifiable() {
    var resource = operator.create(testResource());

    await().untilAsserted(() -> {
      var cm = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
      assertThat(cm).isNotNull();
      assertThat(cm.getData()).containsEntry(UNMODIFIABLE_INITIAL_DATA_KEY, INITIAL_DATA);
      assertThat(cm.getData()).containsEntry(ACTUAL_DATA_KEY, INITIAL_DATA);
    });

    resource.getSpec().setData(UPDATED_DATA);
    operator.replace(resource);

    await().untilAsserted(() -> {
      var cm = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
      assertThat(cm).isNotNull();
      assertThat(cm.getData()).containsEntry(UNMODIFIABLE_INITIAL_DATA_KEY, INITIAL_DATA);
      assertThat(cm.getData()).containsEntry(ACTUAL_DATA_KEY, UPDATED_DATA);
    });
  }


  UnmodifiableDependentPartCustomResource testResource() {
    var res = new UnmodifiableDependentPartCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE_NAME)
        .build());
    res.setSpec(new UnmodifiableDependentPartSpec());
    res.getSpec().setData(INITIAL_DATA);
    return res;
  }

}
