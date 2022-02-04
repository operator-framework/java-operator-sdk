package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Persister;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public class DependentResourceController<R, P extends HasMetadata, C extends DependentResourceConfiguration<R, P>>
    implements DependentResource<R, P>, Persister<R, P> {

  private final Persister<R, P> persister;
  private final DependentResource<R, P> delegate;
  private final C configuration;
  
  public DependentResourceController(DependentResource<R, P> delegate, C configuration) {
    this.delegate = delegate;
    persister = initPersister(delegate);
    this.configuration = configuration;
  }

  @Override
  public Class<R> resourceType() {
    return delegate.resourceType();
  }

  @Override
  public boolean match(R actual, P primary, Context context) {
    return delegate.match(actual, primary, context);
  }

  @Override
  public R desired(P primary, Context context) {
    return delegate.desired(primary, context);
  }

  @Override
  public void delete(R fetched, P primary, Context context) {
    delegate.delete(fetched, primary, context);
  }

  @SuppressWarnings("unchecked")
  protected Persister<R, P> initPersister(DependentResource<R, P> delegate) {
    if (delegate instanceof Persister) {
      return (Persister<R, P>) delegate;
    } else {
      throw new IllegalArgumentException(
          "DependentResource '" + delegate.getClass().getName() + "' must implement Persister");
    }
  }

  public String descriptionFor(R resource) {
    return resource.toString();
  }

  public Class<R> getResourceType() {
    return delegate.resourceType();
  }

  @Override
  public EventSource initEventSource(EventSourceContext<P> context) {
    return delegate.initEventSource(context);
  }

  @Override
  public void createOrReplace(R dependentResource, Context context) {
    persister.createOrReplace(dependentResource, context);
  }

  @Override
  public R getFor(P primary, Context context) {
    return persister.getFor(primary, context);
  }

  public C getConfiguration() {
    return configuration;
  }
}
