package io.javaoperatorsdk.operator.sample.webapp;

import java.util.stream.Collectors;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;
import io.javaoperatorsdk.operator.processing.event.source.InformerEventSource;
import io.javaoperatorsdk.operator.sample.tomcat.resource.Tomcat;
import io.javaoperatorsdk.operator.sample.webapp.resource.Webapp;

public abstract class WebappEventSourceInitializer implements EventSourceInitializer<Webapp> {

  private final KubernetesClient kubernetesClient;

  public WebappEventSourceInitializer(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public void prepareEventSources(EventSourceRegistry<Webapp> eventSourceRegistry) {
    InformerEventSource<Tomcat> tomcatEventSource =
        new InformerEventSource<>(kubernetesClient, Tomcat.class, t -> {
          // To create an event to a related WebApp resource and trigger the reconciliation
          // we need to find which WebApp this Tomcat custom resource is related to.
          // To find the related customResourceId of the WebApp resource we traverse the cache to
          // and identify it based on naming convention.
          var webAppInformer =
              eventSourceRegistry.getControllerResourceEventSource()
                  .getInformer(ControllerResourceEventSource.ANY_NAMESPACE_MAP_KEY);

          var ids = webAppInformer.getStore().list().stream()
              .filter(
                  (Webapp webApp) -> webApp.getSpec().getTomcat()
                      .equals(t.getMetadata().getName()))
              .map(webapp -> new ResourceID(webapp.getMetadata().getName(),
                  webapp.getMetadata().getNamespace()))
              .collect(Collectors.toSet());
          return ids;
        });

    eventSourceRegistry.registerEventSource(tomcatEventSource);
  }

}
