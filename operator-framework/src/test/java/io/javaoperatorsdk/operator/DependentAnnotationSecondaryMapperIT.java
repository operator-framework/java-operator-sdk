package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.dependentannotationsecondarymapper.DependentAnnotationSecondaryMapperReconciler;
import io.javaoperatorsdk.operator.sample.dependentannotationsecondarymapper.DependentAnnotationSecondaryMapperResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DependentAnnotationSecondaryMapperIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder().withReconciler(DependentAnnotationSecondaryMapperReconciler.class)
          .build();

  @Test
  void mapsSecondaryByAnnotation() {
    operator.create(DependentAnnotationSecondaryMapperResource.class, testResource());

    var reconciler =
        operator.getReconcilerOfType(DependentAnnotationSecondaryMapperReconciler.class);

    await().untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isEqualTo(1);
    });
  }


  DependentAnnotationSecondaryMapperResource testResource() {
    var res = new DependentAnnotationSecondaryMapperResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE_NAME)
        .build());
    return res;
  }

}
