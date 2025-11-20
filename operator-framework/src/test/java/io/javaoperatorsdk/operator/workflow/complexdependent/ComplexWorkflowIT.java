package io.javaoperatorsdk.operator.workflow.complexdependent;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.readiness.Readiness;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.workflow.complexdependent.dependent.FirstService;
import io.javaoperatorsdk.operator.workflow.complexdependent.dependent.FirstStatefulSet;
import io.javaoperatorsdk.operator.workflow.complexdependent.dependent.SecondService;
import io.javaoperatorsdk.operator.workflow.complexdependent.dependent.SecondStatefulSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Complex Workflow with Multiple Dependents",
    description =
        "Demonstrates a complex workflow with multiple dependent resources (StatefulSets and"
            + " Services) that have dependencies on each other. This test shows how to orchestrate"
            + " the reconciliation of interconnected dependent resources in a specific order.")
class ComplexWorkflowIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new ComplexWorkflowReconciler()).build();

  @Test
  void successfullyReconciles() {
    operator.create(testResource());

    await()
        .atMost(Duration.ofSeconds(90))
        .untilAsserted(
            () -> {
              var res = operator.get(ComplexWorkflowCustomResource.class, TEST_RESOURCE_NAME);
              assertThat(res.getStatus()).isNotNull();
              assertThat(res.getStatus().getStatus())
                  .isEqualTo(ComplexWorkflowReconciler.RECONCILE_STATUS.READY);
            });

    var firstStatefulSet =
        operator.get(
            StatefulSet.class,
            String.format("%s-%s", FirstStatefulSet.DISCRIMINATOR_PREFIX, TEST_RESOURCE_NAME));
    var secondStatefulSet =
        operator.get(
            StatefulSet.class,
            String.format("%s-%s", SecondStatefulSet.DISCRIMINATOR_PREFIX, TEST_RESOURCE_NAME));
    var firstService =
        operator.get(
            Service.class,
            String.format("%s-%s", FirstService.DISCRIMINATOR_PREFIX, TEST_RESOURCE_NAME));
    var secondService =
        operator.get(
            Service.class,
            String.format("%s-%s", SecondService.DISCRIMINATOR_PREFIX, TEST_RESOURCE_NAME));
    assertThat(firstService).isNotNull();
    assertThat(secondService).isNotNull();
    assertThat(firstStatefulSet).isNotNull();
    assertThat(secondStatefulSet).isNotNull();
    assertThat(Readiness.isStatefulSetReady(firstStatefulSet)).isTrue();
    assertThat(Readiness.isStatefulSetReady(secondStatefulSet)).isTrue();
  }

  ComplexWorkflowCustomResource testResource() {
    var resource = new ComplexWorkflowCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    resource.setSpec(new ComplexWorkflowSpec());
    resource.getSpec().setProjectId(TEST_RESOURCE_NAME);

    return resource;
  }
}
