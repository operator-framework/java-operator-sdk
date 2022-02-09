package io.javaoperatorsdk.operator.dependent;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.standalonedependent.StandaloneDependentTestCustomResource;
import io.javaoperatorsdk.operator.sample.standalonedependent.StandaloneDependentTestReconciler;

import static org.awaitility.Awaitility.await;

class StandaloneDependentResourceIT {

  public static final String DEPENDENT_TEST_NAME = "dependent-test1";

  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withReconciler(new StandaloneDependentTestReconciler())
          .build();

  @Test
  void dependentResourceManagesDeployment() {
    StandaloneDependentTestCustomResource customResource =
        new StandaloneDependentTestCustomResource();
    customResource.setMetadata(new ObjectMeta());
    customResource.getMetadata().setName(DEPENDENT_TEST_NAME);
    var createdCR = operator.create(StandaloneDependentTestCustomResource.class, customResource);

    await()
        .pollInterval(Duration.ofMillis(300))
        .atMost(Duration.ofSeconds(50))
        .until(
            () -> {
              StandaloneDependentTestReconciler reconciler =
                  (StandaloneDependentTestReconciler) operator.getReconcilers().get(0);
              var deployment =
                  operator
                      .getKubernetesClient()
                      .resources(Deployment.class)
                      .inNamespace(createdCR.getMetadata().getNamespace())
                      .withName(DEPENDENT_TEST_NAME)
                      .get();
              return deployment != null
                  && deployment.getStatus() != null
                  && deployment.getStatus().getReadyReplicas() != null
                  && deployment.getStatus().getReadyReplicas() > 0;
            });
  }
}
