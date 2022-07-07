package io.javaoperatorsdk.operator.processing.event.source.filter;

public class CompositeFilter<R> implements EventFilter<R> {
  private final EventFilter<R> delegate;

  public CompositeFilter(EventFilter<R> delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean acceptsAdding(R resource) {
    return delegate.acceptsAdding(resource);
  }

  @Override
  public boolean acceptsUpdating(R from, R to) {
    return delegate.acceptsUpdating(from, to);
  }

  @Override
  public boolean acceptsDeleting(R resource) {
    return delegate.acceptsDeleting(resource);
  }

  @Override
  public boolean rejects(R resource) {
    return delegate.rejects(resource);
  }
}
