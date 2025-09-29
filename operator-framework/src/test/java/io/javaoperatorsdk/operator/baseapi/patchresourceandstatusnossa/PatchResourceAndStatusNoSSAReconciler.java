package io.javaoperatorsdk.operator.baseapi.patchresourceandstatusnossa;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class PatchResourceAndStatusNoSSAReconciler
    implements Reconciler<PatchResourceAndStatusNoSSACustomResource>, TestExecutionInfoProvider {

  private static final Logger log =
      LoggerFactory.getLogger(PatchResourceAndStatusNoSSAReconciler.class);
  public static final String TEST_ANNOTATION = "TestAnnotation";
  public static final String TEST_ANNOTATION_VALUE = "TestAnnotationValue";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<PatchResourceAndStatusNoSSACustomResource> reconcile(
      PatchResourceAndStatusNoSSACustomResource resource,
      Context<PatchResourceAndStatusNoSSACustomResource> context) {
    numberOfExecutions.addAndGet(1);

    log.info("Value: " + resource.getSpec().getValue());

    resource.getMetadata().getAnnotations().remove(TEST_ANNOTATION);
    ensureStatusExists(resource);
    resource.getStatus().setState(PatchResourceAndStatusNoSSAStatus.State.SUCCESS);

    return UpdateControl.patchResourceAndStatus(resource);
  }

  private void ensureStatusExists(PatchResourceAndStatusNoSSACustomResource resource) {
    PatchResourceAndStatusNoSSAStatus status = resource.getStatus();
    if (status == null) {
      status = new PatchResourceAndStatusNoSSAStatus();
      resource.setStatus(status);
    }
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
