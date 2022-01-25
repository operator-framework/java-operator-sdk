package io.javaoperatorsdk.experimental.dependentresource;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public class DependentResourceManager<P extends HasMetadata> {

  private final List<DependentResource<?, P>> dependentResources;
  private final ControlHandler<P> controlHandler;

  public DependentResourceManager(List<DependentResource<?, P>> dependentResources,
      ControlHandler<P> controlHandler) {
    this.dependentResources = dependentResources;
    this.controlHandler = controlHandler;
  }

  public UpdateControl<P> reconcile(P primaryResource, Context context) {
    ReconciliationContext<P> reconciliationContext = new ReconciliationContext<>(primaryResource,
        context, dependentResources);
    dependentResources.forEach(d -> d.reconcile(reconciliationContext));
    return controlHandler.updateControl(reconciliationContext);
  }

  public DeleteControl cleanup(P primaryResource, Context context) {
    ReconciliationContext<P> reconciliationContext =
        new ReconciliationContext<>(primaryResource,
            context, dependentResources);
    return controlHandler.deleteControl(reconciliationContext);
  }

  public List<EventSource> prepareEventSources(EventSourceContext<P> context) {
    return null;
  }

}
