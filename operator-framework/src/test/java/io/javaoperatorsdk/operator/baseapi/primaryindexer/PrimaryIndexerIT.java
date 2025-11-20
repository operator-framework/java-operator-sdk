package io.javaoperatorsdk.operator.baseapi.primaryindexer;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.primaryindexer.AbstractPrimaryIndexerTestReconciler.CONFIG_MAP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Using Primary Indexer for Secondary Resource Mapping",
    description =
        "Demonstrates how to use primary indexers to efficiently map secondary resources back to"
            + " their primary resources. When a secondary resource (like a ConfigMap) changes, the"
            + " primary indexer allows the framework to determine which primary resources should be"
            + " reconciled. This pattern enables efficient one-to-many and many-to-many"
            + " relationships between primary and secondary resources without polling or full"
            + " scans.")
public class PrimaryIndexerIT {

  public static final String RESOURCE_NAME1 = "test1";
  public static final String RESOURCE_NAME2 = "test2";

  @RegisterExtension LocallyRunOperatorExtension operator = buildOperator();

  protected LocallyRunOperatorExtension buildOperator() {
    return LocallyRunOperatorExtension.builder()
        .withReconciler(new PrimaryIndexerTestReconciler())
        .build();
  }

  @Test
  void changesToSecondaryResourcesCorrectlyTriggerReconciler() {
    var reconciler = (AbstractPrimaryIndexerTestReconciler) operator.getFirstReconciler();
    operator.create(createTestResource(RESOURCE_NAME1));
    operator.create(createTestResource(RESOURCE_NAME2));

    await()
        .pollDelay(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions().get(RESOURCE_NAME1).get()).isEqualTo(1);
              assertThat(reconciler.getNumberOfExecutions().get(RESOURCE_NAME2).get()).isEqualTo(1);
            });

    operator.create(configMap());

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
