package io.javaoperatorsdk.operator.sample.dependentssa;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@ControllerConfiguration
public class DependentSSAReconciler
    implements Reconciler<DependnetSSACustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private SSAConfigMapDependent ssaConfigMapDependent = new SSAConfigMapDependent();

  public DependentSSAReconciler() {
    this(true);
  }

  public DependentSSAReconciler(boolean useSSA) {
    ssaConfigMapDependent.configureWith(new KubernetesDependentResourceConfigBuilder<ConfigMap>()
        .withUseSSA(useSSA)
        .build());
  }

  @Override
  public UpdateControl<DependnetSSACustomResource> reconcile(
      DependnetSSACustomResource resource,
      Context<DependnetSSACustomResource> context) {

    ssaConfigMapDependent.reconcile(resource, context);
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<DependnetSSACustomResource> context) {
    return EventSourceUtils.nameEventSourcesFromDependentResource(context,
        ssaConfigMapDependent);
  }
}
