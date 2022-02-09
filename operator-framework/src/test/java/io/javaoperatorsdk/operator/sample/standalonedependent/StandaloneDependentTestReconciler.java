package io.javaoperatorsdk.operator.sample.standalonedependent;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.StandaloneKubernetesDependentResource;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

@ControllerConfiguration(finalizerName = NO_FINALIZER)
public class StandaloneDependentTestReconciler
    implements Reconciler<StandaloneDependentTestCustomResource>,
        EventSourceInitializer<StandaloneDependentTestCustomResource>,
        TestExecutionInfoProvider,
        KubernetesClientAware {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private KubernetesClient kubernetesClient;

  StandaloneKubernetesDependentResource<Deployment, StandaloneDependentTestCustomResource> configMapDependent;

  public StandaloneDependentTestReconciler() {
    configMapDependent = new StandaloneKubernetesDependentResource<>();
    configMapDependent.setResourceType(Deployment.class);
    configMapDependent.setDesiredSupplier(
        (primary, context) -> {
          Deployment deployment = loadYaml(Deployment.class, "nginx-deployment.yaml");
          deployment.getMetadata().setName(primary.getMetadata().getName());
          return deployment;
        });
  }

  @Override
  public List<EventSource> prepareEventSources(
      EventSourceContext<StandaloneDependentTestCustomResource> context) {
    return List.of(configMapDependent.eventSource(context).get());
  }

  @Override
  public UpdateControl<StandaloneDependentTestCustomResource> reconcile(
      StandaloneDependentTestCustomResource resource, Context context) {
    numberOfExecutions.addAndGet(1);
    configMapDependent.reconcile(resource, context);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
    configMapDependent.setClient(kubernetesClient);
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
}
