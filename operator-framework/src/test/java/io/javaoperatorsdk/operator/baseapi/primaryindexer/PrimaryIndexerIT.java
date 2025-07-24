package io.javaoperatorsdk.operator.baseapi.primaryindexer;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.primaryindexer.AbstractPrimaryIndexerTestReconciler.CONFIG_MAP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class PrimaryIndexerIT {

  public static final String RESOURCE_NAME1 = "test1";
  public static final String RESOURCE_NAME2 = "test2";

  @RegisterExtension LocallyRunOperatorExtension extension = buildOperator();

  protected LocallyRunOperatorExtension buildOperator() {
    return LocallyRunOperatorExtension.builder()
        .withReconciler(new PrimaryIndexerTestReconciler())
        .build();
  }

  @Test
  void changesToSecondaryResourcesCorrectlyTriggerReconciler() {
    var reconciler = (AbstractPrimaryIndexerTestReconciler) extension.getFirstReconciler();
    extension.create(createTestResource(RESOURCE_NAME1));
    extension.create(createTestResource(RESOURCE_NAME2));

    await()
        .pollDelay(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions().get(RESOURCE_NAME1).get()).isEqualTo(1);
              assertThat(reconciler.getNumberOfExecutions().get(RESOURCE_NAME2).get()).isEqualTo(1);
            });

    extension.create(configMap());

    await()
        .pollDelay(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions().get(RESOURCE_NAME1).get()).isEqualTo(2);
              assertThat(reconciler.getNumberOfExecutions().get(RESOURCE_NAME2).get()).isEqualTo(2);
            });
  }

  private ConfigMap configMap() {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(CONFIG_MAP_NAME);

    return configMap;
  }

  private PrimaryIndexerTestCustomResource createTestResource(String name) {
    PrimaryIndexerTestCustomResource cr = new PrimaryIndexerTestCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(name);
    cr.setSpec(new PrimaryIndexerTestCustomResourceSpec());
    cr.getSpec().setConfigMapName(CONFIG_MAP_NAME);
    return cr;
  }
}
