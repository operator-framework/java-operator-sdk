package io.javaoperatorsdk.operator.dependent.dependentreinitialization;

import java.util.List;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class DependentReInitializationReconciler
    implements Reconciler<DependentReInitializationCustomResource> {

  private final ConfigMapDependentResource configMapDependentResource;

  public DependentReInitializationReconciler(ConfigMapDependentResource dependentResource) {
    this.configMapDependentResource = dependentResource;
  }

  @Override
  public UpdateControl<DependentReInitializationCustomResource> reconcile(
      DependentReInitializationCustomResource resource,
      Context<DependentReInitializationCustomResource> context)
      throws Exception {
    configMapDependentResource.reconcile(resource, context);
    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, DependentReInitializationCustomResource>> prepareEventSources(
      EventSourceContext<DependentReInitializationCustomResource> context) {
    return EventSourceUtils.dependentEventSources(context, configMapDependentResource);
  }
}
