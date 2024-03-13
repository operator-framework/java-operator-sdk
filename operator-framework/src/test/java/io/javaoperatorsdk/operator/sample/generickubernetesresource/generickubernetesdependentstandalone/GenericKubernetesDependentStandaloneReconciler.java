package io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesdependentstandalone;

import java.util.Map;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class GenericKubernetesDependentStandaloneReconciler
    implements Reconciler<GenericKubernetesDependentStandaloneCustomResource> {

  private final ConfigMapGenericKubernetesDependent dependent =
      new ConfigMapGenericKubernetesDependent();

  public GenericKubernetesDependentStandaloneReconciler() {}

  @Override
  public UpdateControl<GenericKubernetesDependentStandaloneCustomResource> reconcile(
      GenericKubernetesDependentStandaloneCustomResource resource,
      Context<GenericKubernetesDependentStandaloneCustomResource> context) {

    dependent.reconcile(resource, context);

    return UpdateControl.<GenericKubernetesDependentStandaloneCustomResource>noUpdate();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<GenericKubernetesDependentStandaloneCustomResource> context) {
    return EventSourceUtils.nameEventSources(dependent.eventSource(context).orElseThrow());
  }
}
