package io.javaoperatorsdk.operator.sample.externalstate;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Workflow(dependents = @Dependent(type = ExternalWithStateDependentResource.class))
@ControllerConfiguration
public class ExternalStateDependentReconciler
    implements Reconciler<ExternalStateCustomResource>,
    TestExecutionInfoProvider {

  public static final String ID_KEY = "id";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<ExternalStateCustomResource> reconcile(
      ExternalStateCustomResource resource,
      Context<ExternalStateCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<ExternalStateCustomResource> context) {
    var configMapEventSource = new InformerEventSource<>(
        InformerConfiguration.from(ConfigMap.class, context).build(), context);
    return EventSourceUtils.nameEventSources(configMapEventSource);
  }

}
