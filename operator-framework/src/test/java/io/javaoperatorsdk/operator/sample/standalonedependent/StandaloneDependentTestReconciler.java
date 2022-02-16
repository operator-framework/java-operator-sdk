package io.javaoperatorsdk.operator.sample.standalonedependent;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

@ControllerConfiguration(finalizerName = NO_FINALIZER)
public class StandaloneDependentTestReconciler
    implements Reconciler<StandaloneDependentTestCustomResource>,
    EventSourceInitializer<StandaloneDependentTestCustomResource>,
    KubernetesClientAware {

  private KubernetesClient kubernetesClient;

  KubernetesDependentResource<Deployment, StandaloneDependentTestCustomResource> configMapDependent;

  public StandaloneDependentTestReconciler() {
    configMapDependent = new DeploymentDependentResource();
  }

  @Override
  public List<EventSource> prepareEventSources(
      EventSourceContext<StandaloneDependentTestCustomResource> context) {
    return List.of(configMapDependent.eventSource(context).get());
  }

  @Override
  public UpdateControl<StandaloneDependentTestCustomResource> reconcile(
      StandaloneDependentTestCustomResource resource, Context context) {
    configMapDependent.reconcile(resource, context);
    return UpdateControl.noUpdate();
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
    configMapDependent.setKubernetesClient(kubernetesClient);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return this.kubernetesClient;
  }

  private <T> T loadYaml(Class<T> clazz, String yaml) {
    try (InputStream is = getClass().getResourceAsStream(yaml)) {
      return Serialization.unmarshal(is, clazz);
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
    }
  }

  private class DeploymentDependentResource extends
      KubernetesDependentResource<Deployment, StandaloneDependentTestCustomResource> {

    @Override
    protected Deployment desired(StandaloneDependentTestCustomResource primary, Context context) {
      Deployment deployment = StandaloneDependentTestReconciler.this.loadYaml(Deployment.class,
          "nginx-deployment.yaml");
      deployment.getMetadata().setName(primary.getMetadata().getName());
      deployment.getMetadata().setNamespace(primary.getMetadata().getNamespace());
      return deployment;
    }
  }
}
