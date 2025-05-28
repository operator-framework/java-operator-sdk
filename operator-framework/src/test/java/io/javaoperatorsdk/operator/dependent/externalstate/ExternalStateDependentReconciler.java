package io.javaoperatorsdk.operator.dependent.externalstate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(dependents = @Dependent(type = ExternalWithStateDependentResource.class))
@ControllerConfiguration
public class ExternalStateDependentReconciler
    implements Reconciler<ExternalStateCustomResource>, TestExecutionInfoProvider {

  public static final String ID_KEY = "id";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<ExternalStateCustomResource> reconcile(
      ExternalStateCustomResource resource, Context<ExternalStateCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource<?, ExternalStateCustomResource>> prepareEventSources(
      EventSourceContext<ExternalStateCustomResource> context) {
    var configMapEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, ExternalStateCustomResource.class)
                .build(),
            context);
    return List.of(configMapEventSource);
  }
}
