package io.javaoperatorsdk.operator.sample;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

/**
 * Runs a specified number of Tomcat app server Pods. It uses a Deployment to create the Pods. Also
 * creates a Service over which the Pods can be accessed.
 */
@Workflow(
    dependents = {
      @Dependent(type = DeploymentDependentResource.class),
      @Dependent(type = ServiceDependentResource.class)
    })
@ControllerConfiguration
public class TomcatReconciler implements Reconciler<Tomcat> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Override
  public UpdateControl<Tomcat> reconcile(Tomcat tomcat, Context<Tomcat> context) {
    return context
        .getSecondaryResource(Deployment.class)
        .map(
            deployment -> {
              Tomcat updatedTomcat = createTomcatForStatusUpdate(tomcat, deployment);
              log.info(
                  "Updating status of Tomcat {} in namespace {} to {} ready replicas",
                  tomcat.getMetadata().getName(),
                  tomcat.getMetadata().getNamespace(),
                  tomcat.getStatus() == null ? 0 : tomcat.getStatus().getReadyReplicas());
              return UpdateControl.patchStatus(updatedTomcat);
            })
        .orElseGet(UpdateControl::noUpdate);
  }

  private Tomcat createTomcatForStatusUpdate(Tomcat tomcat, Deployment deployment) {
    Tomcat res = new Tomcat();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(tomcat.getMetadata().getName())
            .withNamespace(tomcat.getMetadata().getNamespace())
            .build());
    DeploymentStatus deploymentStatus =
        Objects.requireNonNullElse(deployment.getStatus(), new DeploymentStatus());
    int readyReplicas = Objects.requireNonNullElse(deploymentStatus.getReadyReplicas(), 0);
    TomcatStatus status = new TomcatStatus();
    status.setReadyReplicas(readyReplicas);
    res.setStatus(status);
    return res;
  }
}
