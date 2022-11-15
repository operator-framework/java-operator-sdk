package io.javaoperatorsdk.operator;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Pod;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.builtinresourcecleaner.ObservedGenerationTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BuiltInResourceCleanerIT {

  private static final Logger log = LoggerFactory.getLogger(BuiltInResourceCleanerIT.class);

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ObservedGenerationTestReconciler())
          .build();

  @Test
  void cleanerIsCalledOnBuiltInResource() {
    var pod = operator.create(testPod());

    await().untilAsserted(() -> {
      assertThat(operator.getReconcilerOfType(ObservedGenerationTestReconciler.class)
          .getReconcileCount()).isPositive();
      var actualPod = operator.get(Pod.class, pod.getMetadata().getName());
      assertThat(actualPod.getMetadata().getFinalizers()).isNotEmpty();
    });

    log.info("Deleting pod");
    operator.delete(pod);

    await().untilAsserted(() -> {
      assertThat(operator.getReconcilerOfType(ObservedGenerationTestReconciler.class)
          .getCleanCount()).isPositive();
    });
    log.info("everything ok, actual pod: {}", operator.get(Pod.class, pod.getMetadata().getName()));
  }

  Pod testPod() {
    Pod pod = ReconcilerUtils.loadYaml(Pod.class, StandaloneDependentResourceIT.class,
        "pod-template.yaml");
    pod.getMetadata().setLabels(Map.of("builtintest", "true"));
    return pod;
  }

}
