package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.conditionchecker.ConditionCheckerTestCustomResource;
import io.javaoperatorsdk.operator.sample.conditionchecker.ConditionCheckerTestCustomResourceSpec;
import io.javaoperatorsdk.operator.sample.conditionchecker.ConditionCheckerTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ConditionCheckerIT {

  private static final String DEPENDENT_TEST_NAME = "test1";
  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withReconciler(new ConditionCheckerTestReconciler())
          .build();

  @Test
  void dependentResourceManagesDeployment() {
    ConditionCheckerTestCustomResource customResource =
        new ConditionCheckerTestCustomResource();
    customResource.setSpec(new ConditionCheckerTestCustomResourceSpec());
    customResource.setMetadata(new ObjectMeta());
    customResource.getMetadata().setName(DEPENDENT_TEST_NAME);
    operator.create(ConditionCheckerTestCustomResource.class, customResource);

    await().atMost(Duration.ofSeconds(5))
        .until(() -> operator.get(ConditionCheckerTestCustomResource.class, DEPENDENT_TEST_NAME)
            .getStatus().getReady());

    assertThat(operator.get(ConditionCheckerTestCustomResource.class, DEPENDENT_TEST_NAME)
        .getStatus().getWasNotReadyYet()).isTrue();

  }
}
