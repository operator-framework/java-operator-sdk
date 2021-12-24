package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.config.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

/**
 * Runs a specified number of Tomcat app server Pods. It uses a Deployment to create the Pods. Also
 * creates a Service over which the Pods can be accessed.
 */
@ControllerConfiguration(
    finalizerName = NO_FINALIZER,
    dependents = {
        @Dependent(resourceType = Deployment.class, type = DeploymentDependentResource.class),
        @Dependent(resourceType = Service.class, type = ServiceDependentResource.class)
    })
public class TomcatReconciler implements Reconciler<Tomcat> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Override
  public UpdateControl<Tomcat> reconcile(Tomcat tomcat, Context context) {
    return context.getSecondaryResource(Deployment.class).map(deployment -> {
      Tomcat updatedTomcat = updateTomcatStatus(tomcat, deployment);
      log.info(
          "Updating status of Tomcat {} in namespace {} to {} ready replicas",
          tomcat.getMetadata().getName(),
          tomcat.getMetadata().getNamespace(),
          tomcat.getStatus().getReadyReplicas());
      return UpdateControl.updateStatus(updatedTomcat);
    }).orElse(UpdateControl.noUpdate());
  }

  private Tomcat updateTomcatStatus(Tomcat tomcat, Deployment deployment) {
    DeploymentStatus deploymentStatus =
        Objects.requireNonNullElse(deployment.getStatus(), new DeploymentStatus());
    int readyReplicas = Objects.requireNonNullElse(deploymentStatus.getReadyReplicas(), 0);
    TomcatStatus status = new TomcatStatus();
    status.setReadyReplicas(readyReplicas);
    tomcat.setStatus(status);
    return tomcat;
  }

  static <T> T loadYaml(Class<T> clazz, String yaml) {
    try (InputStream is = TomcatReconciler.class.getResourceAsStream(yaml)) {
      return Serialization.unmarshal(is, clazz);
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
    }
  }
}
