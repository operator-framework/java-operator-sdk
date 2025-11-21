package io.javaoperatorsdk.operator.dependent.servicestrictmatcher;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Strict matching for Service resources",
    description =
        """
        Shows how to use a strict matcher for Service dependent resources that correctly handles \
        Service-specific fields. This prevents unnecessary updates when Kubernetes adds \
        default values or modifies certain fields, avoiding reconciliation loops.
        """)
public class ServiceStrictMatcherIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
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
