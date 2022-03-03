package io.javaoperatorsdk.operator.sample;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.E2EOperatorExtension;
import io.javaoperatorsdk.operator.junit.OperatorExtension;

class WebPageOperatorDependentResourcesE2E extends WebPageOperatorAbstractTest {

  public WebPageOperatorDependentResourcesE2E() throws FileNotFoundException {}

  @RegisterExtension
  AbstractOperatorExtension operator =
      isLocal()
          ? OperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withConfigurationService(DefaultConfigurationService.instance())
              .withReconciler(new WebPageReconcilerDependentResources(client))
              .build()
          : E2EOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withConfigurationService(DefaultConfigurationService.instance())
              .withOperatorDeployment(client.load(new FileInputStream("k8s/operator.yaml")).get())
              .build();

  @Override
  AbstractOperatorExtension operator() {
    return operator;
  }
}
