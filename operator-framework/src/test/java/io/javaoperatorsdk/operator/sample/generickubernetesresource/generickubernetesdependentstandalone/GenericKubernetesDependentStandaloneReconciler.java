package io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesdependentstandalone;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

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
  public List<EventSource> prepareEventSources(
      EventSourceContext<GenericKubernetesDependentStandaloneCustomResource> context) {
    return List.of(dependent.eventSource(context).orElseThrow());
  }
}
