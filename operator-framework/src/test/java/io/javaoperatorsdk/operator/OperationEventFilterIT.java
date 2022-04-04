package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.operationeventfiltering.ConfigMapDependentResource;
import io.javaoperatorsdk.operator.sample.operationeventfiltering.OperationEventFilterCustomResource;
import io.javaoperatorsdk.operator.sample.operationeventfiltering.OperationEventFilterCustomResourceSpec;
import io.javaoperatorsdk.operator.sample.operationeventfiltering.OperationEventFilterCustomResourceTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OperationEventFilterIT {

  public static final String TEST = "test";
  public static final String SPEC_VAL_1 = "val1";
  public static final String SPEC_VAL_2 = "val2";

  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withReconciler(new OperationEventFilterCustomResourceTestReconciler())
          .build();

  @Test
  void reconcileNotTriggeredWithDependentResourceCreateOrUpdate() {
    var resource = operator.create(OperationEventFilterCustomResource.class, createTestResource());

    await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
        .until(
            () -> ((OperationEventFilterCustomResourceTestReconciler) operator.getFirstReconciler())
                .getNumberOfExecutions() == 1);
    assertThat(operator.get(ConfigMap.class, TEST).getData())
        .containsEntry(ConfigMapDependentResource.KEY, SPEC_VAL_1);

    resource.getSpec().setValue(SPEC_VAL_2);
    operator.replace(OperationEventFilterCustomResource.class, resource);

    await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
        .until(
            () -> ((OperationEventFilterCustomResourceTestReconciler) operator.getFirstReconciler())
                .getNumberOfExecutions() == 2);
    assertThat(operator.get(ConfigMap.class, TEST).getData())
        .containsEntry(ConfigMapDependentResource.KEY, SPEC_VAL_2);
  }


  private OperationEventFilterCustomResource createTestResource() {
    OperationEventFilterCustomResource cr = new OperationEventFilterCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(TEST);
    cr.setSpec(new OperationEventFilterCustomResourceSpec());
    cr.getSpec().setValue(SPEC_VAL_1);
    return cr;
  }

}
