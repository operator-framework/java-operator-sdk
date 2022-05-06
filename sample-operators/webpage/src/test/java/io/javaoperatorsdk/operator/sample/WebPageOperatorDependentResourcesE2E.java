package io.javaoperatorsdk.operator.sample;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.E2EOperatorExtension;
import io.javaoperatorsdk.operator.junit.LocalOperatorExtension;

class WebPageOperatorDependentResourcesE2E extends WebPageOperatorAbstractTest {

  public WebPageOperatorDependentResourcesE2E() throws FileNotFoundException {}

  @RegisterExtension
  AbstractOperatorExtension operator =
      isLocal()
          ? LocalOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withReconciler(new WebPageStandaloneDependentsReconciler(client))
              .build()
          : E2EOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withOperatorDeployment(client.load(new FileInputStream("k8s/operator.yaml")).get())
              .build();

  @Override
  AbstractOperatorExtension operator() {
    return operator;
  }
}
