package io.javaoperatorsdk.operator.sample;

import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;

import static java.time.temporal.ChronoUnit.SECONDS;

public class ControllerNamespaceDeletionOperator {

  private static final Logger log =
      LoggerFactory.getLogger(ControllerNamespaceDeletionOperator.class);

  public static void main(String[] args) {

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("Shutting down...");
                  boolean allResourcesDeleted = waitUntilResourcesDeleted();
                  log.info("All resources within timeout: {}", allResourcesDeleted);
                }));

    Operator operator = new Operator();
    operator.register(
        new ControllerNamespaceDeletionReconciler(),
        ControllerConfigurationOverrider::watchingOnlyCurrentNamespace);
    operator.start();
  }

  private static boolean waitUntilResourcesDeleted() {
    try (var client = new KubernetesClientBuilder().build()) {
      var startTime = LocalTime.now();
      while (startTime.until(LocalTime.now(), SECONDS) < 20) {
        var items =
            client
                .resources(ControllerNamespaceDeletionCustomResource.class)
                .inNamespace(client.getConfiguration().getNamespace())
                .list()
                .getItems();
        log.info("Custom resource in namespace: {}", items);
        if (items.isEmpty()) {
          return true;
        }
      }
      return false;
    }
  }
}
