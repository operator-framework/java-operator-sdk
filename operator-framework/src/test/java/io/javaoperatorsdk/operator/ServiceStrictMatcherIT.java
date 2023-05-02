package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.servicestrictmatcher.ServiceDependentResource;
import io.javaoperatorsdk.operator.sample.servicestrictmatcher.ServiceStrictMatcherSpec;
import io.javaoperatorsdk.operator.sample.servicestrictmatcher.ServiceStrictMatcherTestCustomResource;
import io.javaoperatorsdk.operator.sample.servicestrictmatcher.ServiceStrictMatcherTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ServiceStrictMatcherIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new ServiceStrictMatcherTestReconciler())
          .build();


  @Test
  void testTheMatchingDoesNoTTriggersFurtherUpdates() {
    var resource = operator.create(testResource());

    // make an update to spec to reconcile again
    resource.getSpec().setValue(2);
    operator.replace(resource);

    await().pollDelay(Duration.ofMillis(300)).untilAsserted(() -> {
      assertThat(operator.getReconcilerOfType(ServiceStrictMatcherTestReconciler.class)
          .getNumberOfExecutions()).isEqualTo(2);
      assertThat(ServiceDependentResource.updated.get()).isEqualTo(0);
    });
  }


  ServiceStrictMatcherTestCustomResource testResource() {
    var res = new ServiceStrictMatcherTestCustomResource();
    res.setSpec(new ServiceStrictMatcherSpec());
    res.getSpec().setValue(1);
    res.setMetadata(new ObjectMetaBuilder()
        .withName("test1")
        .build());
    return res;
  }

}
