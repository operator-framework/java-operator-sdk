package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.jenvtest.junit.EnableKubeAPIServer;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.primaryindexer.DependentPrimaryIndexerTestReconciler;

@EnableKubeAPIServer
public class DependentPrimaryIndexerIT extends PrimaryIndexerIT {

  static KubernetesClient client;

  protected LocallyRunOperatorExtension buildOperator() {
    return LocallyRunOperatorExtension.builder()
        .withKubernetesClient(client)
        .waitForNamespaceDeletion(false)
        .withReconciler(new DependentPrimaryIndexerTestReconciler())
        .build();
  }
}
