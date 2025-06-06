package io.javaoperatorsdk.operator.dependent.prevblocklist;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PrevAnnotationBlockReconcilerIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          // Removing resource from blocklist List would result in test failure
          //          .withConfigurationService(
          //              o -> o.previousAnnotationUsageBlocklist(Collections.emptyList()))
          .withReconciler(PrevAnnotationBlockReconciler.class)
          .build();

  @Test
  void doNotUsePrevAnnotationForDeploymentDependent() {
    extension.create(testResource(TEST_1));

    var reconciler = extension.getReconcilerOfType(PrevAnnotationBlockReconciler.class);
    await()
        .pollDelay(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              var deployment = extension.get(Deployment.class, TEST_1);
              assertThat(deployment).isNotNull();
              assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(0).isLessThan(10);
            });
  }

  PrevAnnotationBlockCustomResource testResource(String name) {
    var res = new PrevAnnotationBlockCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(name).build());
    res.setSpec(new PrevAnnotationBlockSpec());
    res.getSpec().setValue("value");
    return res;
  }
}
