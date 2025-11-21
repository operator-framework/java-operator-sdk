package io.javaoperatorsdk.operator.baseapi.unmodifiabledependentpart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.unmodifiabledependentpart.UnmodifiablePartConfigMapDependent.ACTUAL_DATA_KEY;
import static io.javaoperatorsdk.operator.baseapi.unmodifiabledependentpart.UnmodifiablePartConfigMapDependent.UNMODIFIABLE_INITIAL_DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Unmodifiable Parts in Dependent Resources",
    description =
        """
        Demonstrates how to preserve certain parts of a dependent resource from being modified \
        during updates while allowing other parts to change. This test shows that initial data \
        can be marked as unmodifiable and will remain unchanged even when the primary resource \
        spec is updated, enabling partial update control.
        """)
public class UnmodifiableDependentPartIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String INITIAL_DATA = "initialData";
  public static final String UPDATED_DATA = "updatedData";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(UnmodifiableDependentPartReconciler.class)
          .build();

  @Test
  void partConfigMapDataUnmodifiable() {
    var resource = operator.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var cm = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(cm).isNotNull();
              assertThat(cm.getData()).containsEntry(UNMODIFIABLE_INITIAL_DATA_KEY, INITIAL_DATA);
              assertThat(cm.getData()).containsEntry(ACTUAL_DATA_KEY, INITIAL_DATA);
            });

    resource.getSpec().setData(UPDATED_DATA);
    operator.replace(resource);

    await()
        .untilAsserted(
            () -> {
              var cm = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(cm).isNotNull();
              assertThat(cm.getData()).containsEntry(UNMODIFIABLE_INITIAL_DATA_KEY, INITIAL_DATA);
              assertThat(cm.getData()).containsEntry(ACTUAL_DATA_KEY, UPDATED_DATA);
            });
  }

  UnmodifiableDependentPartCustomResource testResource() {
    var res = new UnmodifiableDependentPartCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    res.setSpec(new UnmodifiableDependentPartSpec());
    res.getSpec().setData(INITIAL_DATA);
    return res;
  }
}
