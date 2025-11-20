package io.javaoperatorsdk.operator.baseapi.builtinresourcecleaner;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.dependent.standalonedependent.StandaloneDependentResourceIT;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Cleanup handler for built-in Kubernetes resources",
    description =
        "Demonstrates how to implement cleanup handlers (finalizers) for built-in Kubernetes"
            + " resources like Service and Pod. These resources don't use generation the same way"
            + " as custom resources, so this sample shows the proper approach to handle their"
            + " lifecycle and cleanup logic.")
class BuiltInResourceCleanerIT {

  private static final Logger log = LoggerFactory.getLogger(BuiltInResourceCleanerIT.class);

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new BuiltInResourceCleanerReconciler())
          .build();

  /**
   * Issue is with generation, some built in resources like Pod, Service does not seem to use
   * generation.
   */
  @Test
  void cleanerIsCalledOnBuiltInResource() {
    var service = operator.create(testService());

    await()
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(BuiltInResourceCleanerReconciler.class)
                          .getReconcileCount())
                  .isPositive();
              var actualService = operator.get(Service.class, service.getMetadata().getName());
              assertThat(actualService.getMetadata().getFinalizers()).isNotEmpty();
            });

    operator.delete(service);

    await()
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(BuiltInResourceCleanerReconciler.class)
                          .getCleanCount())
                  .isPositive();
            });
  }

  Service testService() {
    Service service =
        ReconcilerUtils.loadYaml(
            Service.class,
            StandaloneDependentResourceIT.class,
            "/io/javaoperatorsdk/operator/service-template.yaml");
    service.getMetadata().setLabels(Map.of("builtintest", "true"));
    return service;
  }
}
