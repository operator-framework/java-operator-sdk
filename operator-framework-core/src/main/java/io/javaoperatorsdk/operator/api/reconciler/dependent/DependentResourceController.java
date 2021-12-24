package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public class DependentResourceController<R, P extends HasMetadata>
    implements DependentResource<R, P>, Builder<R, P>, Updater<R, P>, Persister<R, P>,
    Cleaner<R, P> {

  private final Builder<R, P> builder;
  private final Updater<R, P> updater;
  private final Cleaner<R, P> cleaner;
  private final Persister<R, P> persister;
  private final DependentResource<R, P> delegate;

  @SuppressWarnings("unchecked")
  public DependentResourceController(DependentResource<R, P> delegate) {
    this.delegate = delegate;
    builder = (delegate instanceof Builder) ? (Builder<R, P>) delegate : null;
    updater = (delegate instanceof Updater) ? (Updater<R, P>) delegate : null;
    cleaner = (delegate instanceof Cleaner) ? (Cleaner<R, P>) delegate : null;
    persister = initPersister(delegate);
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

  @Override
  public R buildFor(P primary, Context context) {
    return builder.buildFor(primary, context);
  }

  @Override
  public R update(R fetched, P primary, Context context) {
    return updater.update(fetched, primary, context);
  }

  @Override
  public void delete(R fetched, P primary, Context context) {
    cleaner.delete(fetched, primary, context);
  }

  public Class<R> getResourceType() {
    return delegate.resourceType();
  }

  @Override
  public EventSource initEventSource(EventSourceContext<P> context) {
    return delegate.initEventSource(context);
  }

  public boolean creatable() {
    return builder != null;
  }

  public boolean updatable() {
    return updater != null;
  }

  public boolean deletable() {
    return cleaner != null;
  }

  @Override
  public void createOrReplace(R dependentResource, Context context) {
    persister.createOrReplace(dependentResource, context);
  }

  @Override
  public R getFor(P primary, Context context) {
    return persister.getFor(primary, context);
  }
}
