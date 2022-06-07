package io.javaoperatorsdk.operator.sample.doubleupdate;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;

@ControllerConfiguration
public class DoubleUpdateTestCustomReconciler
    extends AbstractExecutionNumberRecordingReconciler<DoubleUpdateTestCustomResource> {

  private static final Logger log =
      LoggerFactory.getLogger(DoubleUpdateTestCustomReconciler.class);
  public static final String TEST_ANNOTATION = "TestAnnotation";
  public static final String TEST_ANNOTATION_VALUE = "TestAnnotationValue";

  @Override
  public UpdateControl<DoubleUpdateTestCustomResource> reconcile(
      DoubleUpdateTestCustomResource resource, Context<DoubleUpdateTestCustomResource> context) {
    recordReconcileExecution();

    log.info("Value: " + resource.getSpec().getValue());

    resource.getMetadata().setAnnotations(new HashMap<>());
    resource.getMetadata().getAnnotations().put(TEST_ANNOTATION, TEST_ANNOTATION_VALUE);
    ensureStatusExists(resource);
    resource.getStatus().setState(DoubleUpdateTestCustomResourceStatus.State.SUCCESS);

    return UpdateControl.updateResourceAndStatus(resource);
  }

  private void ensureStatusExists(DoubleUpdateTestCustomResource resource) {
    DoubleUpdateTestCustomResourceStatus status = resource.getStatus();
    if (status == null) {
      status = new DoubleUpdateTestCustomResourceStatus();
      resource.setStatus(status);
    }
  }
}
