package io.javaoperatorsdk.operator.baseapi.expectation.simple;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

class SimpleExpectationSampleIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new SimpleExpectationReconciler())
          .build();

  @Test
  void exceptDeploymentUp() {}

  public SimpleExpectationCustomResource createCustomResource() {
    SimpleExpectationCustomResource resource = new SimpleExpectationCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("error-status-test")
            .withNamespace(operator.getNamespace())
            .build());
    return resource;
  }
}
