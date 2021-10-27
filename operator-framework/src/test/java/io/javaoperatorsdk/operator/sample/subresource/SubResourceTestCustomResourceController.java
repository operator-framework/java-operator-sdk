package io.javaoperatorsdk.operator.sample.subresource;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

@Controller(generationAwareEventProcessing = false)
public class SubResourceTestCustomResourceController
    implements ResourceController<SubResourceTestCustomResource>, TestExecutionInfoProvider {

  public static final String FINALIZER_NAME =
      ControllerUtils.getDefaultFinalizerName(
          CustomResource.getCRDName(SubResourceTestCustomResource.class));
  private static final Logger log =
      LoggerFactory.getLogger(SubResourceTestCustomResourceController.class);
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<SubResourceTestCustomResource> createOrUpdateResource(
          SubResourceTestCustomResource resource, Context context) {
    numberOfExecutions.addAndGet(1);
    if (!resource.getMetadata().getFinalizers().contains(FINALIZER_NAME)) {
      throw new IllegalStateException("Finalizer is not present.");
    }
    log.info("Value: " + resource.getSpec().getValue());

    ensureStatusExists(resource);
    resource.getStatus().setState(SubResourceTestCustomResourceStatus.State.SUCCESS);

    return UpdateControl.updateStatusSubResource(resource);
  }

  private void ensureStatusExists(SubResourceTestCustomResource resource) {
    SubResourceTestCustomResourceStatus status = resource.getStatus();
    if (status == null) {
      status = new SubResourceTestCustomResourceStatus();
      resource.setStatus(status);
    }
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
