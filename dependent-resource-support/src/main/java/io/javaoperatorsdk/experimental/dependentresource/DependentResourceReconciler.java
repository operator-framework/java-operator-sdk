package io.javaoperatorsdk.experimental.dependentresource;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

// Todo
// 1. design for non k8s resources
// 1.1. Finalizer: how to override based on the type of resources present -> if non k8s needs
// finalizer
// 2. resources without cache?

public class DependentResourceReconciler<T extends HasMetadata>
    implements Reconciler<T>, EventSourceInitializer<T>,
    ErrorStatusHandler<T> {

  private final DependentResourceManager<T> dependentResourceManager;
  private ErrorStatusHandler<T> errorStatusHandler;

  public DependentResourceReconciler(List<DependentResource<?, T>> dependentResources,
      ControlHandler<T> controlHandler) {

    this(dependentResources, controlHandler, null);
  }

  public DependentResourceReconciler(List<DependentResource<?, T>> dependentResources,
      ControlHandler<T> controlHandler, ErrorStatusHandler<T> errorStatusHandler) {
    this.errorStatusHandler = errorStatusHandler;
    dependentResourceManager = new DependentResourceManager(dependentResources, controlHandler);
  }

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<T> context) {
    return dependentResourceManager.prepareEventSources(context);
  }

  @Override
  public UpdateControl<T> reconcile(T resource, Context context) {
    return dependentResourceManager.reconcile(resource, context);
  }

  @Override
  public DeleteControl cleanup(T resource, Context context) {
    return dependentResourceManager.cleanup(resource, context);
  }

  @Override
  public Optional<T> updateErrorStatus(T resource, RetryInfo retryInfo, RuntimeException e) {
    if (this.errorStatusHandler != null) {
      return this.errorStatusHandler.updateErrorStatus(resource, retryInfo, e);
    } else {
      return Optional.empty();
    }
  }
}
