package io.javaoperatorsdk.operator.sample.errorstatushandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;

@ControllerConfiguration
public class ErrorStatusHandlerTestReconciler
    extends AbstractExecutionNumberRecordingReconciler<ErrorStatusHandlerTestCustomResource>
    implements ErrorStatusHandler<ErrorStatusHandlerTestCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(ErrorStatusHandlerTestReconciler.class);
  public static final String ERROR_STATUS_MESSAGE = "Error Retries Exceeded";

  @Override
  public UpdateControl<ErrorStatusHandlerTestCustomResource> reconcile(
      ErrorStatusHandlerTestCustomResource resource,
      Context<ErrorStatusHandlerTestCustomResource> context) {
    var number = recordReconcileExecution();
    var retryAttempt = -1;
    if (context.getRetryInfo().isPresent()) {
      retryAttempt = context.getRetryInfo().get().getAttemptCount();
    }
    log.info("Number of execution: {}  retry attempt: {} , resource: {}", number, retryAttempt,
        resource);
    throw new IllegalStateException();
  }

  private void ensureStatusExists(ErrorStatusHandlerTestCustomResource resource) {
    ErrorStatusHandlerTestCustomResourceStatus status = resource.getStatus();
    if (status == null) {
      status = new ErrorStatusHandlerTestCustomResourceStatus();
      resource.setStatus(status);
    }
  }

  @Override
  public ErrorStatusUpdateControl<ErrorStatusHandlerTestCustomResource> updateErrorStatus(
      ErrorStatusHandlerTestCustomResource resource,
      Context<ErrorStatusHandlerTestCustomResource> context, Exception e) {
    log.info("Setting status.");
    ensureStatusExists(resource);
    resource.getStatus().getMessages()
        .add(ERROR_STATUS_MESSAGE + context.getRetryInfo().orElseThrow().getAttemptCount());
    return ErrorStatusUpdateControl.updateStatus(resource);
  }
}
