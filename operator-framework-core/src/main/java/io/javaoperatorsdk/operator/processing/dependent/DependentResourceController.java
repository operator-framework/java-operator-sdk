package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

// todo delete controllers
@Ignore
public class DependentResourceController<R, P extends HasMetadata, C extends DependentResourceConfiguration<R, P>, D extends DependentResource<R, P>>
    implements DependentResource<R, P> {

  protected final D delegate;
  protected final C configuration;

  public DependentResourceController(D delegate, C configuration) {
    this.delegate = delegate;
    this.configuration = configuration;
    applyConfigurationToDelegate(delegate,configuration);
  }

  protected void applyConfigurationToDelegate(D delegate, C configuration) {
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
