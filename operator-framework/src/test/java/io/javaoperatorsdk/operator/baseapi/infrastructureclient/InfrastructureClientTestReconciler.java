package io.javaoperatorsdk.operator.baseapi.infrastructureclient;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(name = InfrastructureClientTestReconciler.TEST_RECONCILER)
public class InfrastructureClientTestReconciler
    implements Reconciler<InfrastructureClientTestCustomResource>, TestExecutionInfoProvider {

  private static final Logger log =
      LoggerFactory.getLogger(InfrastructureClientTestReconciler.class);

  public static final String TEST_RECONCILER = "InfrastructureClientTestReconciler";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<InfrastructureClientTestCustomResource> reconcile(
      InfrastructureClientTestCustomResource resource,
      Context<InfrastructureClientTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    log.info("Reconciled for: {}", ResourceID.fromResource(resource));
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
