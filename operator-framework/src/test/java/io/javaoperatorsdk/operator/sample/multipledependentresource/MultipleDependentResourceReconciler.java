package io.javaoperatorsdk.operator.sample.multipledependentresource;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
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
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<MultipleDependentResourceCustomResource> context) {
    InformerEventSource<ConfigMap, MultipleDependentResourceCustomResource> eventSource =
        new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
            .build(), context);
    firstDependentResourceConfigMap.configureWith(eventSource);
    secondDependentResourceConfigMap.configureWith(eventSource);

    return EventSourceUtils.nameEventSources(eventSource);
  }
}
