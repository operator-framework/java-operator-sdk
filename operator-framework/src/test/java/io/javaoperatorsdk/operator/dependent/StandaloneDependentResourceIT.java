package io.javaoperatorsdk.operator.dependent;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.standalonedependent.StandaloneDependentTestCustomResource;
import io.javaoperatorsdk.operator.sample.standalonedependent.StandaloneDependentTestReconciler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;

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
    operator.create(StandaloneDependentTestCustomResource.class, customResource);


    await()
        .atMost(Duration.ofSeconds(55))
        .until(
            () -> {
              StandaloneDependentTestReconciler reconciler =
                  (StandaloneDependentTestReconciler) operator.getReconcilers().get(0);

              return reconciler.getNumberOfExecutions() > 3;
            });

    System.out.println("test print");
  }
}
