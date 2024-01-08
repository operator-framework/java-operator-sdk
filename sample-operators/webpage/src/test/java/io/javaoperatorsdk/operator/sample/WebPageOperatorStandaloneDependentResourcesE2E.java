package io.javaoperatorsdk.operator.sample;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.ClusterDeployedOperatorExtension;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

class WebPageOperatorStandaloneDependentResourcesE2E extends WebPageOperatorAbstractTest {

  public WebPageOperatorStandaloneDependentResourcesE2E() throws FileNotFoundException {}

  @RegisterExtension
  AbstractOperatorExtension operator =
      isLocal()
          ? LocallyRunOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withReconciler(new WebPageStandaloneDependentsReconciler())
              .build()
          : ClusterDeployedOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withOperatorDeployment(client.load(new FileInputStream("k8s/operator.yaml")).items())
              .build();

  @Override
  AbstractOperatorExtension operator() {
    return operator;
  }
}
