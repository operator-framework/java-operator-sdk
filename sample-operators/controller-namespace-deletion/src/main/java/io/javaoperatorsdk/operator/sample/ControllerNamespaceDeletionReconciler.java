package io.javaoperatorsdk.operator.sample;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

public class ControllerNamespaceDeletionReconciler
    implements Reconciler<ControllerNamespaceDeletionCustomResource>,
        Cleaner<ControllerNamespaceDeletionCustomResource> {

  private static final Logger log =
      LoggerFactory.getLogger(ControllerNamespaceDeletionReconciler.class);

  public static final Duration CLEANUP_DELAY = Duration.ofSeconds(10);

  @Override
  public UpdateControl<ControllerNamespaceDeletionCustomResource> reconcile(
      ControllerNamespaceDeletionCustomResource resource,
      Context<ControllerNamespaceDeletionCustomResource> context) {
    log.info(
        "Reconciling: {} in namespace: {}",
        resource.getMetadata().getName(),
        resource.getMetadata().getNamespace());

    var response = createResponseResource(resource);
    response.getStatus().setValue(resource.getSpec().getValue());

    return UpdateControl.patchStatus(response);
  }

  private ControllerNamespaceDeletionCustomResource createResponseResource(
      ControllerNamespaceDeletionCustomResource resource) {
    var res = new ControllerNamespaceDeletionCustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());
    res.setStatus(new ControllerNamespaceDeletionStatus());
    return res;
  }

  @Override
  public DeleteControl cleanup(
      ControllerNamespaceDeletionCustomResource resource,
      Context<ControllerNamespaceDeletionCustomResource> context) {
    log.info("Cleaning up resource");
    try {
      Thread.sleep(CLEANUP_DELAY.toMillis());
      return DeleteControl.defaultDelete();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
