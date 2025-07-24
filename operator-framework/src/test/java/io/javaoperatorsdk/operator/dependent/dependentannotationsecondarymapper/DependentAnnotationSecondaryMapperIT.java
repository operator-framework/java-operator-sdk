package io.javaoperatorsdk.operator.dependent.dependentannotationsecondarymapper;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.processing.event.source.informer.Mappers.DEFAULT_ANNOTATION_FOR_NAME;
import static io.javaoperatorsdk.operator.processing.event.source.informer.Mappers.DEFAULT_ANNOTATION_FOR_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DependentAnnotationSecondaryMapperIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  static LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(DependentAnnotationSecondaryMapperReconciler.class)
          .build();

  @Test
  void mapsSecondaryByAnnotation() {
    operator.create(testResource());

    var reconciler =
        operator.getReconcilerOfType(DependentAnnotationSecondaryMapperReconciler.class);

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(() -> assertThat(reconciler.getNumberOfExecutions()).isEqualTo(1));
    var configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);

    var annotations = configMap.getMetadata().getAnnotations();

    assertThat(annotations)
        .containsEntry(DEFAULT_ANNOTATION_FOR_NAME, TEST_RESOURCE_NAME)
        .containsEntry(DEFAULT_ANNOTATION_FOR_NAMESPACE, operator.getNamespace());

    assertThat(configMap.getMetadata().getOwnerReferences()).isEmpty();

    configMap.getData().put("additional_data", "data");
    operator.replace(configMap);

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(() -> assertThat(reconciler.getNumberOfExecutions()).isEqualTo(2));
  }

  DependentAnnotationSecondaryMapperResource testResource() {
    var res = new DependentAnnotationSecondaryMapperResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}
