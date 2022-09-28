package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

public class EmptyTestDependentResource
    implements DependentResource<Deployment, TestCustomResource> {

  @Override
  public ReconcileResult<Deployment> reconcile(TestCustomResource primary,
      Context<TestCustomResource> context) {
    return null;
  }

  @Override
  public Class<Deployment> resourceType() {
    return Deployment.class;
  }

}

