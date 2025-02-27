package io.javaoperatorsdk.operator.dependent.multipledrsametypenodiscriminator;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import static io.javaoperatorsdk.operator.dependent.multiplemanageddependentsametype.MultipleManagedDependentResourceReconciler.CONFIG_MAP_EVENT_SOURCE;

@Workflow(
    dependents = {
      @Dependent(
          type = MultipleManagedDependentNoDiscriminatorConfigMap1.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
      @Dependent(
          type = MultipleManagedDependentNoDiscriminatorConfigMap2.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE)
    })
@ControllerConfiguration
public class MultipleManagedDependentSameTypeNoDiscriminatorReconciler
    implements Reconciler<MultipleManagedDependentNoDiscriminatorCustomResource>,
        TestExecutionInfoProvider {

  public static final String CONFIG_MAP_EVENT_SOURCE = "ConfigMapEventSource";
  public static final String DATA_KEY = "key";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  public MultipleManagedDependentSameTypeNoDiscriminatorReconciler() {}

  @Override
  public UpdateControl<MultipleManagedDependentNoDiscriminatorCustomResource> reconcile(
      MultipleManagedDependentNoDiscriminatorCustomResource resource,
      Context<MultipleManagedDependentNoDiscriminatorCustomResource> context) {
    numberOfExecutions.getAndIncrement();

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource<?, MultipleManagedDependentNoDiscriminatorCustomResource>>
      prepareEventSources(
          EventSourceContext<MultipleManagedDependentNoDiscriminatorCustomResource> context) {
    InformerEventSource<ConfigMap, MultipleManagedDependentNoDiscriminatorCustomResource> ies =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, MultipleManagedDependentNoDiscriminatorCustomResource.class)
                .withName(CONFIG_MAP_EVENT_SOURCE)
                .build(),
            context);

    return List.of(ies);
  }
}
