package io.javaoperatorsdk.operator.sample.dependentreinitialization;

import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class DependentReInitializationReconciler
    implements Reconciler<DependentReInitializationCustomResource>,
    EventSourceInitializer<DependentReInitializationCustomResource> {

  private final ConfigMapDependentResource configMapDependentResource;

  public DependentReInitializationReconciler(ConfigMapDependentResource dependentResource,
      KubernetesClient client) {
    this.configMapDependentResource = dependentResource;
    this.configMapDependentResource.setKubernetesClient(client);
  }

  @Override
  public UpdateControl<DependentReInitializationCustomResource> reconcile(
      DependentReInitializationCustomResource resource,
      Context<DependentReInitializationCustomResource> context) throws Exception {
    configMapDependentResource.reconcile(resource, context);
    return UpdateControl.noUpdate();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<DependentReInitializationCustomResource> context) {
    return EventSourceInitializer.nameEventSourcesFromDependentResource(context,
        configMapDependentResource);
  }


}
