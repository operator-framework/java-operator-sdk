package io.javaoperatorsdk.operator.sample.multiplemanageddependent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(dependents = {
    @Dependent(type = MultipleManagedDependentResourceConfigMap1.class),
    @Dependent(type = MultipleManagedDependentResourceConfigMap2.class)
})
public class MultipleManagedDependentResourceReconciler
    implements Reconciler<MultipleManagedDependentResourceCustomResource>,
    TestExecutionInfoProvider,
    EventSourceInitializer<MultipleManagedDependentResourceCustomResource> {

  public static final String CONFIG_MAP_EVENT_SOURCE = "ConfigMapEventSource";
  public static final String DATA_KEY = "key";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  public MultipleManagedDependentResourceReconciler() {}

  @Override
  public UpdateControl<MultipleManagedDependentResourceCustomResource> reconcile(
      MultipleManagedDependentResourceCustomResource resource,
      Context<MultipleManagedDependentResourceCustomResource> context) {
    numberOfExecutions.getAndIncrement();

    return UpdateControl.noUpdate();
  }


  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<MultipleManagedDependentResourceCustomResource> context) {
    InformerEventSource<ConfigMap, MultipleManagedDependentResourceCustomResource> ies =
        new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
            .build(), context);

    return Map.of(CONFIG_MAP_EVENT_SOURCE, ies);
  }
}
