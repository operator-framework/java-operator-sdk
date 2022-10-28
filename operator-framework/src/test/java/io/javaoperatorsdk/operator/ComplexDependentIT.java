package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.readiness.Readiness;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.complexdependent.ComplexDependentCustomResource;
import io.javaoperatorsdk.operator.sample.complexdependent.ComplexDependentReconciler;
import io.javaoperatorsdk.operator.sample.complexdependent.ComplexDependentSpec;
import io.javaoperatorsdk.operator.sample.complexdependent.dependent.FirstService;
import io.javaoperatorsdk.operator.sample.complexdependent.dependent.FirstStatefulSet;
import io.javaoperatorsdk.operator.sample.complexdependent.dependent.SecondService;
import io.javaoperatorsdk.operator.sample.complexdependent.dependent.SecondStatefulSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ComplexDependentIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  Logger log = LoggerFactory.getLogger(ComplexDependentIT.class);

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ComplexDependentReconciler())
          .build();

  @Test
  void successfullyReconciles() {
    operator.create(testResource());

    await().atMost(Duration.ofSeconds(60))
        .untilAsserted(() -> {
          var res = operator.get(ComplexDependentCustomResource.class, TEST_RESOURCE_NAME);
          assertThat(res.getStatus()).isNotNull();
          assertThat(res.getStatus().getStatus())
              .isEqualTo(ComplexDependentReconciler.RECONCILE_STATUS.READY);
        });

    var firstStatefulSet = operator.get(StatefulSet.class, String.format("%s-%s",
        FirstStatefulSet.DISCRIMINATOR_PREFIX, TEST_RESOURCE_NAME));
    var secondStatefulSet = operator.get(StatefulSet.class, String.format("%s-%s",
        SecondStatefulSet.DISCRIMINATOR_PREFIX, TEST_RESOURCE_NAME));
    var firstService = operator.get(Service.class, String.format("%s-%s",
        FirstService.DISCRIMINATOR_PREFIX, TEST_RESOURCE_NAME));
    var secondService = operator.get(Service.class, String.format("%s-%s",
        SecondService.DISCRIMINATOR_PREFIX, TEST_RESOURCE_NAME));
    assertThat(firstService).isNotNull();
    assertThat(secondService).isNotNull();
    assertThat(firstStatefulSet).isNotNull();
    assertThat(secondStatefulSet).isNotNull();
    assertThat(Readiness.isStatefulSetReady(firstStatefulSet)).isTrue();
    assertThat(Readiness.isStatefulSetReady(secondStatefulSet)).isTrue();
  }

  ComplexDependentCustomResource testResource() {
    var resource = new ComplexDependentCustomResource();
    resource.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE_NAME)
        .build());
    resource.setSpec(new ComplexDependentSpec());
    resource.getSpec().setProjectId(TEST_RESOURCE_NAME);

    return resource;
  }

}
