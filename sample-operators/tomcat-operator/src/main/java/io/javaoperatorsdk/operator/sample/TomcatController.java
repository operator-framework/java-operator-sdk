package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs a specified number of Tomcat app server Pods. It uses a Deployment to create the Pods. Also creates a
 * Service over which the Pods can be accessed.
 */
@Controller
public class TomcatController implements ResourceController<Tomcat> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final KubernetesClient kubernetesClient;

  private DeploymentEventSource deploymentEventSource;

  public TomcatController(KubernetesClient client) {
    this.kubernetesClient = client;
  }

  @Override
  public void init(EventSourceManager eventSourceManager) {
    this.deploymentEventSource = DeploymentEventSource.createAndRegisterWatch(kubernetesClient);
    eventSourceManager.registerEventSource("deployment-event-source", this.deploymentEventSource);
  }

  @Override
  public UpdateControl<Tomcat> createOrUpdateResource(Tomcat tomcat, Context<Tomcat> context) {
    Optional<CustomResourceEvent> latestCREvent =
        context.getEvents().getLatestOfType(CustomResourceEvent.class);
    if (latestCREvent.isPresent()) {
      createOrUpdateDeployment(tomcat);
      createOrUpdateService(tomcat);
    }

    Optional<DeploymentEvent> latestDeploymentEvent =
        context.getEvents().getLatestOfType(DeploymentEvent.class);
    if (latestDeploymentEvent.isPresent()) {
      Tomcat updatedTomcat =
          updateTomcatStatus(tomcat, latestDeploymentEvent.get().getDeployment());
      log.info(
          "Updating status of Tomcat {} in namespace {} to {} ready replicas",
          tomcat.getMetadata().getName(),
          tomcat.getMetadata().getNamespace(),
          tomcat.getStatus().getReadyReplicas());
      return UpdateControl.updateStatusSubResource(updatedTomcat);
    }

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl deleteResource(Tomcat tomcat, Context<Tomcat> context) {
    deleteDeployment(tomcat);
    deleteService(tomcat);
    return DeleteControl.DEFAULT_DELETE;
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

  private void createOrUpdateDeployment(Tomcat tomcat) {
    String ns = tomcat.getMetadata().getNamespace();
    Deployment existingDeployment =
        kubernetesClient
            .apps()
            .deployments()
            .inNamespace(ns)
            .withName(tomcat.getMetadata().getName())
            .get();
    if (existingDeployment == null) {
      Deployment deployment = loadYaml(Deployment.class, "deployment.yaml");
      deployment.getMetadata().setName(tomcat.getMetadata().getName());
      deployment.getMetadata().setNamespace(ns);
      deployment.getMetadata().getLabels().put("app.kubernetes.io/part-of", tomcat.getMetadata().getName());
      deployment.getMetadata().getLabels().put("app.kubernetes.io/managed-by", "tomcat-operator");
      // set tomcat version
      deployment
          .getSpec()
          .getTemplate()
          .getSpec()
          .getContainers()
          .get(0)
          .setImage("tomcat:" + tomcat.getSpec().getVersion());
      deployment.getSpec().setReplicas(tomcat.getSpec().getReplicas());

      // make sure label selector matches label (which has to be matched by service selector too)
      deployment
          .getSpec()
          .getTemplate()
          .getMetadata()
          .getLabels()
          .put("app", tomcat.getMetadata().getName());
      deployment
          .getSpec()
          .getSelector()
          .getMatchLabels()
          .put("app", tomcat.getMetadata().getName());

      OwnerReference ownerReference = deployment.getMetadata().getOwnerReferences().get(0);
      ownerReference.setName(tomcat.getMetadata().getName());
      ownerReference.setUid(tomcat.getMetadata().getUid());

      log.info("Creating or updating Deployment {} in {}", deployment.getMetadata().getName(), ns);
      kubernetesClient.apps().deployments().inNamespace(ns).create(deployment);
    } else {
      existingDeployment
          .getSpec()
          .getTemplate()
          .getSpec()
          .getContainers()
          .get(0)
          .setImage("tomcat:" + tomcat.getSpec().getVersion());
      existingDeployment.getSpec().setReplicas(tomcat.getSpec().getReplicas());
      kubernetesClient.apps().deployments().inNamespace(ns).createOrReplace(existingDeployment);
    }
  }

  private void deleteDeployment(Tomcat tomcat) {
    log.info("Deleting Deployment {}", tomcat.getMetadata().getName());
    RollableScalableResource<Deployment> deployment =
        kubernetesClient
            .apps()
            .deployments()
            .inNamespace(tomcat.getMetadata().getNamespace())
            .withName(tomcat.getMetadata().getName());
    if (deployment.get() != null) {
      deployment.delete();
    }
  }

  private void createOrUpdateService(Tomcat tomcat) {
    Service service = loadYaml(Service.class, "service.yaml");
    service.getMetadata().setName(tomcat.getMetadata().getName());
    String ns = tomcat.getMetadata().getNamespace();
    service.getMetadata().setNamespace(ns);
    service.getSpec().getSelector().put("app", tomcat.getMetadata().getName());
    log.info("Creating or updating Service {} in {}", service.getMetadata().getName(), ns);
    kubernetesClient.services().inNamespace(ns).createOrReplace(service);
  }

  private void deleteService(Tomcat tomcat) {
    log.info("Deleting Service {}", tomcat.getMetadata().getName());
    ServiceResource<Service> service =
        kubernetesClient
            .services()
            .inNamespace(tomcat.getMetadata().getNamespace())
            .withName(tomcat.getMetadata().getName());
    if (service.get() != null) {
      service.delete();
    }
  }

  private <T> T loadYaml(Class<T> clazz, String yaml) {
    try (InputStream is = getClass().getResourceAsStream(yaml)) {
      return Serialization.unmarshal(is, clazz);
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
    }
  }
}
