package io.javaoperatorsdk.operator.baseapi.manualobservedgeneration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Manually managing observedGeneration in status",
    description =
        "Shows how to manually track and update the observedGeneration field in status to indicate"
            + " which generation of the resource spec has been successfully processed. This is"
            + " useful for providing clear feedback to users about reconciliation progress.")
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
