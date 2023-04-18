package io.javaoperatorsdk.operator;

import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.jenvtest.junit.EnableKubeAPIServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.builtinresourcecleaner.ObservedGenerationTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnableKubeAPIServer
class BuiltInResourceCleanerIT {

  private static final Logger log = LoggerFactory.getLogger(BuiltInResourceCleanerIT.class);

  static KubernetesClient client;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
              .withKubernetesClient(client)
              .waitForNamespaceDeletion(false)
          .withReconciler(new ObservedGenerationTestReconciler())
          .build();

  /**
   * Issue is with generation, some built in resources like Pod, Service does not seem to use
   * generation.
   */
  @Test
  void cleanerIsCalledOnBuiltInResource() {
    var service = operator.create(testService());

    await().untilAsserted(() -> {
      assertThat(operator.getReconcilerOfType(ObservedGenerationTestReconciler.class)
          .getReconcileCount()).isPositive();
      var actualService = operator.get(Service.class, service.getMetadata().getName());
      assertThat(actualService.getMetadata().getFinalizers()).isNotEmpty();
    });

    operator.delete(service);

    await().untilAsserted(() -> {
      assertThat(operator.getReconcilerOfType(ObservedGenerationTestReconciler.class)
          .getCleanCount()).isPositive();
    });
  }

  Service testService() {
    Service service = ReconcilerUtils.loadYaml(Service.class, StandaloneDependentResourceIT.class,
        "service-template.yaml");
    service.getMetadata().setLabels(Map.of("builtintest", "true"));
    return service;
  }

}
