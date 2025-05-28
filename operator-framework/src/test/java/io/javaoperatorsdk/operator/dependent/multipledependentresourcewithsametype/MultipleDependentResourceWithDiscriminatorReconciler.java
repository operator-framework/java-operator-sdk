package io.javaoperatorsdk.operator.dependent.multipledependentresourcewithsametype;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class MultipleDependentResourceWithDiscriminatorReconciler
    implements Reconciler<MultipleDependentResourceCustomResourceNoDiscriminator>,
        TestExecutionInfoProvider {

  public static final int FIRST_CONFIG_MAP_ID = 1;
  public static final int SECOND_CONFIG_MAP_ID = 2;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final MultipleDependentResourceConfigMap firstDependentResourceConfigMap;
  private final MultipleDependentResourceConfigMap secondDependentResourceConfigMap;

  public MultipleDependentResourceWithDiscriminatorReconciler() {
    firstDependentResourceConfigMap = new MultipleDependentResourceConfigMap(FIRST_CONFIG_MAP_ID);
    secondDependentResourceConfigMap = new MultipleDependentResourceConfigMap(SECOND_CONFIG_MAP_ID);
  }

  @Override
  public UpdateControl<MultipleDependentResourceCustomResourceNoDiscriminator> reconcile(
      MultipleDependentResourceCustomResourceNoDiscriminator resource,
      Context<MultipleDependentResourceCustomResourceNoDiscriminator> context) {
    numberOfExecutions.getAndIncrement();
    firstDependentResourceConfigMap.reconcile(resource, context);
    secondDependentResourceConfigMap.reconcile(resource, context);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource<?, MultipleDependentResourceCustomResourceNoDiscriminator>>
      prepareEventSources(
          EventSourceContext<MultipleDependentResourceCustomResourceNoDiscriminator> context) {
    InformerEventSource<ConfigMap, MultipleDependentResourceCustomResourceNoDiscriminator>
        eventSource =
            new InformerEventSource<>(
                InformerEventSourceConfiguration.from(
                        ConfigMap.class,
                        MultipleDependentResourceCustomResourceNoDiscriminator.class)
                    .build(),
                context);
    firstDependentResourceConfigMap.setEventSource(eventSource);
    secondDependentResourceConfigMap.setEventSource(eventSource);

    return List.of(eventSource);
  }
}
