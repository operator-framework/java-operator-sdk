package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;
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

    await().pollDelay(Duration.ofMillis(150)).untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isEqualTo(1);
    });
    var configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);

    var annotations = configMap.getMetadata().getAnnotations();
    assertThat(annotations.get(Mappers.DEFAULT_ANNOTATION_FOR_NAME)).isEqualTo(TEST_RESOURCE_NAME);
    assertThat(annotations.get(Mappers.DEFAULT_ANNOTATION_FOR_NAMESPACE))
        .isEqualTo(operator.getNamespace());
    assertThat(configMap.getMetadata().getOwnerReferences()).isEmpty();

    configMap.getData().put("additional_data", "data");
    operator.replace(ConfigMap.class, configMap);

    await().pollDelay(Duration.ofMillis(150)).untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isEqualTo(2);
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
