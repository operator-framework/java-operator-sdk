package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@Ignore
public class DependentResourceController<R, P extends HasMetadata, C extends DependentResourceConfiguration<R, P>, D extends DependentResource<R, P>>
    implements DependentResource<R, P> {

  private final D delegate;
  private final C configuration;

  public DependentResourceController(D delegate, C configuration) {
    this.delegate = delegate;
    this.configuration = initConfiguration(delegate, configuration);
  }

  protected C initConfiguration(D delegate, C configuration) {
    // default implementation just returns the specified one
    return configuration;
  }

  @Override
  public Class<R> resourceType() {
    return delegate.resourceType();
  }

  @Override
  public void delete(P primary, Context context) {
    delegate.delete(primary, context);
  }

  @Override
  public Optional<R> getResource(P primaryResource) {
    return delegate.getResource(primaryResource);
  }


  @Override
  public Optional<EventSource> eventSource(EventSourceContext<P> context) {
    return delegate.eventSource(context);
  }


  public C getConfiguration() {
    return configuration;
  }

  protected D delegate() {
    return delegate;
  }

  @Override
  public void reconcile(P resource, Context context) {
    delegate.reconcile(resource, context);
  }


}
