package io.javaoperatorsdk.operator.dependent.multipledependentresource;

import java.util.List;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class MultipleDependentResourceReconciler
    implements Reconciler<MultipleDependentResourceCustomResource> {

  public static final String FIRST_CONFIG_MAP_ID = "1";
  public static final String SECOND_CONFIG_MAP_ID = "2";

  private final MultipleDependentResourceConfigMap firstDependentResourceConfigMap;
  private final MultipleDependentResourceConfigMap secondDependentResourceConfigMap;

  public MultipleDependentResourceReconciler() {
    firstDependentResourceConfigMap = new MultipleDependentResourceConfigMap(FIRST_CONFIG_MAP_ID);
    secondDependentResourceConfigMap = new MultipleDependentResourceConfigMap(SECOND_CONFIG_MAP_ID);
  }

  @Override
  public UpdateControl<MultipleDependentResourceCustomResource> reconcile(
      MultipleDependentResourceCustomResource resource,
      Context<MultipleDependentResourceCustomResource> context) {
    firstDependentResourceConfigMap.reconcile(resource, context);
    secondDependentResourceConfigMap.reconcile(resource, context);
    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, MultipleDependentResourceCustomResource>> prepareEventSources(
      EventSourceContext<MultipleDependentResourceCustomResource> context) {
    InformerEventSource<ConfigMap, MultipleDependentResourceCustomResource> eventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, MultipleDependentResourceCustomResource.class)
                .build(),
            context);
    firstDependentResourceConfigMap.setEventSource(eventSource);
    secondDependentResourceConfigMap.setEventSource(eventSource);

    return List.of(eventSource);
  }
}
