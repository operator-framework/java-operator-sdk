package io.javaoperatorsdk.operator.dependent.servicestrictmatcher;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ServiceStrictMatcherIT {

  @RegisterExtension
  static LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ServiceStrictMatcherTestReconciler())
          .build();

  @Test
  void testTheMatchingDoesNoTTriggersFurtherUpdates() {
    var resource = operator.create(testResource());

    await()
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(ServiceStrictMatcherTestReconciler.class)
                          .getNumberOfExecutions())
                  .isEqualTo(1);
            });

    // make an update to spec to reconcile again
    resource.getSpec().setValue(2);
    operator.replace(resource);

    await()
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(ServiceStrictMatcherTestReconciler.class)
                          .getNumberOfExecutions())
                  .isEqualTo(2);
              assertThat(ServiceDependentResource.updated.get()).isZero();
            });
  }

  ServiceStrictMatcherTestCustomResource testResource() {
    var res = new ServiceStrictMatcherTestCustomResource();
    res.setSpec(new ServiceStrictMatcherSpec());
    res.getSpec().setValue(1);
    res.setMetadata(new ObjectMetaBuilder().withName("test1").build());
    return res;
  }
}
