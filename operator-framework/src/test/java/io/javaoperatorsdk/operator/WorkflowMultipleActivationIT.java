package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.workflowmultipleactivation.*;

import static io.javaoperatorsdk.operator.sample.workflowactivationcondition.ConfigMapDependentResource.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WorkflowMultipleActivationIT {

  public static final String INITIAL_DATA = "initial data";
  public static final String TEST_RESOURCE = "test1";
  public static final String CHANGED_VALUE = "changed value";
  public static final int POLL_DELAY = 300;

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(WorkflowMultipleActivationReconciler.class)
          .build();

  @Test
  void deactivatingAndReactivatingDependent() {
    ActivationCondition.MET = true;
    var cr1 = extension.create(testResource());

    await().untilAsserted(() -> {
      var cm = extension.get(ConfigMap.class, TEST_RESOURCE);
      var secret = extension.get(Secret.class, TEST_RESOURCE);
      assertThat(cm).isNotNull();
      assertThat(secret).isNotNull();
      assertThat(cm.getData()).containsEntry(DATA_KEY, INITIAL_DATA);
    });

    extension.delete(cr1);

    await().untilAsserted(() -> {
      var cm = extension.get(ConfigMap.class, TEST_RESOURCE);
      assertThat(cm).isNull();
    });

    ActivationCondition.MET = false;
    cr1 = extension.create(testResource());

    await().untilAsserted(() -> {
      var cm = extension.get(ConfigMap.class, TEST_RESOURCE);
      var secret = extension.get(Secret.class, TEST_RESOURCE);
      assertThat(cm).isNull();
      assertThat(secret).isNotNull();
    });

    ActivationCondition.MET = true;
    cr1.getSpec().setValue(CHANGED_VALUE);
    extension.replace(cr1);

    await().untilAsserted(() -> {
      var cm = extension.get(ConfigMap.class, TEST_RESOURCE);
      assertThat(cm).isNotNull();
      assertThat(cm.getData()).containsEntry(DATA_KEY, CHANGED_VALUE);
    });

    ActivationCondition.MET = false;
    cr1.getSpec().setValue(INITIAL_DATA);
    extension.replace(cr1);

    await().pollDelay(Duration.ofMillis(POLL_DELAY)).untilAsserted(() -> {
      var cm = extension.get(ConfigMap.class, TEST_RESOURCE);
      assertThat(cm).isNotNull();
      // data not changed
      assertThat(cm.getData()).containsEntry(DATA_KEY, CHANGED_VALUE);
    });

    var numOfReconciliation =
        extension.getReconcilerOfType(WorkflowMultipleActivationReconciler.class)
            .getNumberOfReconciliationExecution();
    var actualCM = extension.get(ConfigMap.class, TEST_RESOURCE);
    actualCM.getData().put("data2", "additionaldata");
    extension.replace(actualCM);
    await().pollDelay(Duration.ofMillis(POLL_DELAY)).untilAsserted(() -> {
      // change in config map does not induce reconciliation if inactive (thus informer is not
      // present)
      assertThat(extension.getReconcilerOfType(WorkflowMultipleActivationReconciler.class)
          .getNumberOfReconciliationExecution()).isEqualTo(numOfReconciliation);
    });

    extension.delete(cr1);
    await().pollDelay(Duration.ofMillis(POLL_DELAY)).untilAsserted(() -> {
      var cm = extension.get(ConfigMap.class, TEST_RESOURCE);
      assertThat(cm).isNotNull();
    });
  }

  WorkflowMultipleActivationCustomResource testResource() {
    var res = new WorkflowMultipleActivationCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE)
        .build());
    res.setSpec(new WorkflowMultipleActivationSpec());
    res.getSpec().setValue(INITIAL_DATA);
    return res;
  }

}
