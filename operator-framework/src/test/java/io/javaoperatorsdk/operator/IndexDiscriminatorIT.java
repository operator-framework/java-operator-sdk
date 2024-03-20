package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.indexdiscriminator.IndexDiscriminatorTestCustomResource;
import io.javaoperatorsdk.operator.sample.indexdiscriminator.IndexDiscriminatorTestReconciler;
import io.javaoperatorsdk.operator.sample.indexdiscriminator.IndexDiscriminatorTestSpec;

import static io.javaoperatorsdk.operator.sample.indexdiscriminator.IndexDiscriminatorTestDRConfigMap.DATA_KEY;
import static io.javaoperatorsdk.operator.sample.indexdiscriminator.IndexDiscriminatorTestReconciler.FIRST_CONFIG_MAP_SUFFIX_1;
import static io.javaoperatorsdk.operator.sample.indexdiscriminator.IndexDiscriminatorTestReconciler.FIRST_CONFIG_MAP_SUFFIX_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class IndexDiscriminatorIT {

  public static final String TEST_RESOURCE_1 = "test1";
  public static final String CHANGED_SPEC_VALUE = "otherValue";
  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(IndexDiscriminatorTestReconciler.class)
          .build();

  @Test
  void resourcesFoundAndReconciled() {
    var res = operator.create(createTestCustomResource());
    var reconciler = operator.getReconcilerOfType(IndexDiscriminatorTestReconciler.class);

    await().untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isEqualTo(1);
      assertThat(operator.get(ConfigMap.class, TEST_RESOURCE_1 + FIRST_CONFIG_MAP_SUFFIX_1))
          .isNotNull();
      assertThat(operator.get(ConfigMap.class, TEST_RESOURCE_1 + FIRST_CONFIG_MAP_SUFFIX_2))
          .isNotNull();
    });

    res.getSpec().setValue(CHANGED_SPEC_VALUE);
    res = operator.replace(res);

    await().untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isEqualTo(2);
      var cm1 = operator.get(ConfigMap.class, TEST_RESOURCE_1 + FIRST_CONFIG_MAP_SUFFIX_1);
      var cm2 = operator.get(ConfigMap.class, TEST_RESOURCE_1 + FIRST_CONFIG_MAP_SUFFIX_2);
      assertThat(cm1).isNotNull();
      assertThat(cm2).isNotNull();
      assertThat(cm1.getData().get(DATA_KEY)).isEqualTo(CHANGED_SPEC_VALUE);
      assertThat(cm2.getData().get(DATA_KEY)).isEqualTo(CHANGED_SPEC_VALUE);
    });

    operator.delete(res);

    await().untilAsserted(() -> {
      var cm1 = operator.get(ConfigMap.class, TEST_RESOURCE_1 + FIRST_CONFIG_MAP_SUFFIX_1);
      var cm2 = operator.get(ConfigMap.class, TEST_RESOURCE_1 + FIRST_CONFIG_MAP_SUFFIX_2);
      assertThat(cm1).isNull();
      assertThat(cm2).isNull();
    });
  }

  public IndexDiscriminatorTestCustomResource createTestCustomResource() {
    IndexDiscriminatorTestCustomResource resource =
        new IndexDiscriminatorTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST_RESOURCE_1)
            .withNamespace(operator.getNamespace())
            .build());
    resource.setSpec(new IndexDiscriminatorTestSpec());
    resource.getSpec().setValue("default");
    return resource;
  }

}
