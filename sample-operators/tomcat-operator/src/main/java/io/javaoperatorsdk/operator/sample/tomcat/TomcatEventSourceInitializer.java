package io.javaoperatorsdk.operator.sample.tomcat;

import java.util.Set;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;
import io.javaoperatorsdk.operator.processing.event.source.InformerEventSource;
import io.javaoperatorsdk.operator.sample.tomcat.resource.Tomcat;

public class TomcatEventSourceInitializer implements EventSourceInitializer<Tomcat> {

  private final KubernetesClient kubernetesClient;
  private final InformerEventSource<Deployment> informerEventSource;

  public static TomcatEventSourceInitializer createInstance(KubernetesClient kubernetesClient) {
    return new TomcatEventSourceInitializer(kubernetesClient);
  }

  private TomcatEventSourceInitializer(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
    this.informerEventSource = initInformer();
  }

  private InformerEventSource<Deployment> initInformer() {
    final var deploymentInformer =
        kubernetesClient.apps()
            .deployments()
            .inAnyNamespace()
            .withLabel("app.kubernetes.io/managed-by", "tomcat-operator")
            .runnableInformer(0);

    final Function<Deployment, Set<ResourceID>> getResourceIdsFromOwnerReferences = deployment -> {
      var ownerReferences = deployment.getMetadata().getOwnerReferences();

      if (!ownerReferences.isEmpty()) {
        return Set.of(new ResourceID(ownerReferences.get(0).getName(),
            deployment.getMetadata().getNamespace()));
      }
      return Set.of();
    };

    return new InformerEventSource<>(deploymentInformer, getResourceIdsFromOwnerReferences);
  }

  public InformerEventSource<Deployment> getInformerEventSource() {
    return informerEventSource;
  }

  @Override
  public void prepareEventSources(EventSourceRegistry<Tomcat> eventSourceRegistry) {
    eventSourceRegistry.registerEventSource(informerEventSource);
  }
}
