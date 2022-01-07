package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;
import static java.util.Collections.EMPTY_SET;

/**
 * Runs a specified number of Tomcat app server Pods. It uses a Deployment to create the Pods. Also
 * creates a Service over which the Pods can be accessed.
 */
@ControllerConfiguration(finalizerName = NO_FINALIZER)
public class TomcatReconciler implements Reconciler<Tomcat>, EventSourceInitializer<Tomcat> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final KubernetesClient kubernetesClient;

  public TomcatReconciler(KubernetesClient client) {
    this.kubernetesClient = client;
  }

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<Tomcat> context) {
    SharedIndexInformer<Deployment> deploymentInformer =
        kubernetesClient.apps().deployments().inAnyNamespace()
            .withLabel("app.kubernetes.io/managed-by", "tomcat-operator")
            .runnableInformer(0);

    return List.of(new InformerEventSource<>(
        deploymentInformer, d -> {
          var ownerReferences = d.getMetadata().getOwnerReferences();
          if (!ownerReferences.isEmpty()) {
            return Set.of(new ResourceID(ownerReferences.get(0).getName(),
                d.getMetadata().getNamespace()));
          } else {
            return EMPTY_SET;
          }
        }));
  }

  @Override
  public UpdateControl<Tomcat> reconcile(Tomcat tomcat, Context context) {
    createOrUpdateDeployment(tomcat);
    createOrUpdateService(tomcat);

    return context.getSecondaryResource(Deployment.class)
        .map(deployment -> {
          Tomcat updatedTomcat =
              updateTomcatStatus(tomcat, deployment);
          log.info(
              "Updating status of Tomcat {} in namespace {} to {} ready replicas",
              tomcat.getMetadata().getName(),
              tomcat.getMetadata().getNamespace(),
              tomcat.getStatus().getReadyReplicas());
          return UpdateControl.updateStatus(updatedTomcat);
        })
        .orElse(UpdateControl.noUpdate());
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
      deployment.getMetadata().getLabels().put("app.kubernetes.io/part-of",
          tomcat.getMetadata().getName());
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

  private void createOrUpdateService(Tomcat tomcat) {
    Service service = loadYaml(Service.class, "service.yaml");
    service.getMetadata().setName(tomcat.getMetadata().getName());
    String ns = tomcat.getMetadata().getNamespace();
    service.getMetadata().setNamespace(ns);
    service.getMetadata().getOwnerReferences().get(0).setName(tomcat.getMetadata().getName());
    service.getMetadata().getOwnerReferences().get(0).setUid(tomcat.getMetadata().getUid());
    service.getSpec().getSelector().put("app", tomcat.getMetadata().getName());
    log.info("Creating or updating Service {} in {}", service.getMetadata().getName(), ns);
    kubernetesClient.services().inNamespace(ns).createOrReplace(service);
  }

  private <T> T loadYaml(Class<T> clazz, String yaml) {
    try (InputStream is = getClass().getResourceAsStream(yaml)) {
      return Serialization.unmarshal(is, clazz);
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
    }
  }
}
