package io.javaoperatorsdk.operator.sample.externalstate.externalstatebulkdependent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(
    dependents = @Dependent(type = ExternalWithStateBulkDependentResource.class))
public class ExternalStateBulkDependentReconciler
    implements Reconciler<ExternalStateBulkDependentCustomResource>,
    EventSourceInitializer<ExternalStateBulkDependentCustomResource>,
    TestExecutionInfoProvider {

  public static final String ID_KEY = "id";
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
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<ExternalStateBulkDependentCustomResource> context) {
    var configMapEventSource = new InformerEventSource<>(
        InformerConfiguration.from(ConfigMap.class, context).build(), context);
    return EventSourceInitializer.nameEventSources(configMapEventSource);
  }

}
