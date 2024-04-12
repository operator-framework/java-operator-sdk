package io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesdependentstandalone;

import java.util.Map;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceUtils;
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
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<GenericKubernetesDependentStandaloneCustomResource> context) {
    return EventSourceUtils.nameEventSources(dependent.eventSource(context).orElseThrow());
  }
}
