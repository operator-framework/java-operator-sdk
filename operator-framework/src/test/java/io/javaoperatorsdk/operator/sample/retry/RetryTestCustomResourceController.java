package io.javaoperatorsdk.operator.sample.retry;

import io.javaoperatorsdk.operator.TestExecutionInfoProvider;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller(crdName = RetryTestCustomResourceController.CRD_NAME)
public class RetryTestCustomResourceController
    implements ResourceController<RetryTestCustomResource>, TestExecutionInfoProvider {

  public static final int NUMBER_FAILED_EXECUTIONS = 2;

  public static final String CRD_NAME = "retrysamples.sample.javaoperatorsdk";
  public static final String FINALIZER_NAME = CRD_NAME + "/finalizer";
  private static final Logger log =
      LoggerFactory.getLogger(RetryTestCustomResourceController.class);
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public DeleteControl deleteResource(
      RetryTestCustomResource resource, Context<RetryTestCustomResource> context) {
    return DeleteControl.DEFAULT_DELETE;
  }

  @Override
  public UpdateControl<RetryTestCustomResource> createOrUpdateResource(
      RetryTestCustomResource resource, Context<RetryTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    if (!resource.getMetadata().getFinalizers().contains(FINALIZER_NAME)) {
      throw new IllegalStateException("Finalizer is not present.");
    }
    log.info("Value: " + resource.getSpec().getValue());

    if (numberOfExecutions.get() < NUMBER_FAILED_EXECUTIONS + 1) {
      throw new RuntimeException("Testing Retry");
    }
    if (context.getRetryInfo().isEmpty() || context.getRetryInfo().get().isLastAttempt() == true) {
      throw new IllegalStateException("Not expected retry info: " + context.getRetryInfo());
    }

    ensureStatusExists(resource);
    resource.getStatus().setState(RetryTestCustomResourceStatus.State.SUCCESS);

    return UpdateControl.updateStatusSubResource(resource);
  }

  private void ensureStatusExists(RetryTestCustomResource resource) {
    RetryTestCustomResourceStatus status = resource.getStatus();
    if (status == null) {
      status = new RetryTestCustomResourceStatus();
      resource.setStatus(status);
    }
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
