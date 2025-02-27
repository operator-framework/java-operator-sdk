package io.javaoperatorsdk.operator.baseapi.manualobservedgeneration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ManualObservedGenerationIT {

  public static final String RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ManualObservedGenerationReconciler())
          .build();

  @Test
  void observedGenerationUpdated() {
    extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var r = extension.get(ManualObservedGenerationCustomResource.class, RESOURCE_NAME);
              assertThat(r).isNotNull();
              assertThat(r.getStatus().getObservedGeneration()).isEqualTo(1);
              assertThat(r.getStatus().getObservedGeneration())
                  .isEqualTo(r.getMetadata().getGeneration());
            });

    var changed = testResource();
    changed.getSpec().setValue("changed value");
    extension.replace(changed);

    await()
        .untilAsserted(
            () -> {
              var r = extension.get(ManualObservedGenerationCustomResource.class, RESOURCE_NAME);
              assertThat(r.getStatus().getObservedGeneration()).isEqualTo(2);
              assertThat(r.getStatus().getObservedGeneration())
                  .isEqualTo(r.getMetadata().getGeneration());
            });
  }

  ManualObservedGenerationCustomResource testResource() {
    var res = new ManualObservedGenerationCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    res.setSpec(new ManualObservedGenerationSpec());
    res.getSpec().setValue("Initial Value");
    return res;
  }
}
