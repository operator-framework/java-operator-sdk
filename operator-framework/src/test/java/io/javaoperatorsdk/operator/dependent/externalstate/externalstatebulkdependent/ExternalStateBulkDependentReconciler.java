package io.javaoperatorsdk.operator.dependent.externalstate.externalstatebulkdependent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(dependents = @Dependent(type = BulkDependentResourceExternalWithState.class))
@ControllerConfiguration
public class ExternalStateBulkDependentReconciler
    implements Reconciler<ExternalStateBulkDependentCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<ExternalStateBulkDependentCustomResource> reconcile(
      ExternalStateBulkDependentCustomResource resource,
      Context<ExternalStateBulkDependentCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource<?, ExternalStateBulkDependentCustomResource>> prepareEventSources(
      EventSourceContext<ExternalStateBulkDependentCustomResource> context) {
    var configMapEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, ExternalStateBulkDependentCustomResource.class)
                .build(),
            context);
    return List.of(configMapEventSource);
  }
}
