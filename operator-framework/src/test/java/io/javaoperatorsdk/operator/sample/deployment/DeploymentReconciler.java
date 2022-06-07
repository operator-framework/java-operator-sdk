package io.javaoperatorsdk.operator.sample.deployment;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;

@ControllerConfiguration(labelSelector = "test=KubernetesResourceStatusUpdateIT")
public class DeploymentReconciler
    extends AbstractExecutionNumberRecordingReconciler<Deployment> {

  public static final String STATUS_MESSAGE = "Reconciled by DeploymentReconciler";

  private static final Logger log = LoggerFactory.getLogger(DeploymentReconciler.class);

  @Override
  public UpdateControl<Deployment> reconcile(
      Deployment resource, Context<Deployment> context) {

    log.info("Reconcile deployment: {}", resource);
    recordReconcileExecution();
    if (resource.getStatus() == null) {
      resource.setStatus(new DeploymentStatus());
    }
    if (resource.getStatus().getConditions() == null) {
      resource.getStatus().setConditions(new ArrayList<>());
    }
    var conditions = resource.getStatus().getConditions();
    var condition =
        conditions.stream().filter(c -> c.getMessage().equals(STATUS_MESSAGE)).findFirst();
    if (condition.isEmpty()) {
      conditions.add(new DeploymentCondition(null, null, STATUS_MESSAGE, null,
          "unknown", "DeploymentReconciler"));
      return UpdateControl.patchStatus(resource);
    } else {
      return UpdateControl.noUpdate();
    }
  }
}
