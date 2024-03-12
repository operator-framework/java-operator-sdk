package io.javaoperatorsdk.operator.sample.dependentssa;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class DependentSSAReconciler
    implements Reconciler<DependentSSACustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final SSAConfigMapDependent ssaConfigMapDependent = new SSAConfigMapDependent();
  private final boolean useSSA;

  public DependentSSAReconciler() {
    this(true);
  }

  public DependentSSAReconciler(boolean useSSA) {
    ssaConfigMapDependent.configureWith(new KubernetesDependentResourceConfigBuilder<ConfigMap>()
        .withUseSSA(useSSA)
        .build());
    this.useSSA = useSSA;
  }

  public boolean isUseSSA() {
    return useSSA;
  }

  public SSAConfigMapDependent getSsaConfigMapDependent() {
    return ssaConfigMapDependent;
  }

  @Override
  public UpdateControl<DependentSSACustomResource> reconcile(
      DependentSSACustomResource resource,
      Context<DependentSSACustomResource> context) {

    ssaConfigMapDependent.reconcile(resource, context);
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<DependentSSACustomResource> context) {
    return EventSourceUtils.nameEventSourcesFromDependentResource(context,
        ssaConfigMapDependent);
  }
}
