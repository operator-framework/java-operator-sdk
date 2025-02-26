package io.javaoperatorsdk.operator.dependent.generickubernetesresource.generickubernetesdependentstandalone;

import java.util.List;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
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

    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, GenericKubernetesDependentStandaloneCustomResource>>
      prepareEventSources(
          EventSourceContext<GenericKubernetesDependentStandaloneCustomResource> context) {
    return List.of(dependent.eventSource(context).orElseThrow());
  }
}
