package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@Ignore
public class DependentResourceController<R, P extends HasMetadata, C extends DependentResourceConfiguration<R, P>>
    implements DependentResource<R, P> {

  private static final Logger log = LoggerFactory.getLogger(DependentResourceController.class);

  protected final DependentResource<R, P> delegate;
  private final C configuration;

  public DependentResourceController(DependentResource<R, P> delegate, C configuration) {
    this.delegate = delegate;
    this.configuration = configuration;
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

  @Override
  public void reconcile(P resource, Context context) {
    delegate.reconcile(resource, context);
  }


}
