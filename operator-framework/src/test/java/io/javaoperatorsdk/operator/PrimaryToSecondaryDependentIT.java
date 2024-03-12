package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.primarytosecondaydependent.PrimaryToSecondaryDependentCustomResource;
import io.javaoperatorsdk.operator.sample.primarytosecondaydependent.PrimaryToSecondaryDependentReconciler;
import io.javaoperatorsdk.operator.sample.primarytosecondaydependent.PrimaryToSecondaryDependentSpec;

import static io.javaoperatorsdk.operator.sample.primarytosecondaydependent.ConfigMapDependent.TEST_CONFIG_MAP_NAME;
import static io.javaoperatorsdk.operator.sample.primarytosecondaydependent.ConfigMapReconcilePrecondition.DO_NOT_RECONCILE;
import static io.javaoperatorsdk.operator.sample.primarytosecondaydependent.PrimaryToSecondaryDependentReconciler.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PrimaryToSecondaryDependentIT {


  public static final String TEST_CR_NAME = "test1";
  public static final String TEST_DATA = "testData";
  public

  @RegisterExtension LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new PrimaryToSecondaryDependentReconciler())
          .build();

  @Test
  void testPrimaryToSecondaryInDependentResources() {
    var reconciler = operator.getReconcilerOfType(PrimaryToSecondaryDependentReconciler.class);
    var cm = operator.create(configMap(DO_NOT_RECONCILE));
    operator.create(testCustomResource());

    await().pollDelay(Duration.ofMillis(250)).untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isPositive();
      assertThat(operator.get(Secret.class, TEST_CR_NAME)).isNull();
    });

    cm.setData(Map.of(DATA_KEY, TEST_DATA));
    var executions = reconciler.getNumberOfExecutions();
    operator.replace(cm);

    await().pollDelay(Duration.ofMillis(250)).untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(executions);
      var secret = operator.get(Secret.class, TEST_CR_NAME);
      assertThat(secret).isNotNull();
      assertThat(secret.getData().get(DATA_KEY)).isEqualTo(TEST_DATA);
    });
  }

  PrimaryToSecondaryDependentCustomResource testCustomResource() {
    var res = new PrimaryToSecondaryDependentCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_CR_NAME)
        .build());
    res.setSpec(new PrimaryToSecondaryDependentSpec());
    res.getSpec().setConfigMapName(TEST_CONFIG_MAP_NAME);
    return res;
  }

  ConfigMap configMap(String data) {
    var cm = new ConfigMap();
    cm.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_CONFIG_MAP_NAME)
        .build());
    cm.setData(Map.of(DATA_KEY, data));
    return cm;
  }

}
