package io.javaoperatorsdk.operator.processing.event.source;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;

public abstract class AbstractResourceEventSource<R, P extends HasMetadata>
    extends AbstractEventSource
    implements ResourceEventSource<R, P> {
  private final Class<R> resourceClass;

  protected Predicate<R> onAddFilter;
  protected BiPredicate<R, R> onUpdateFilter;
  protected BiPredicate<R, Boolean> onDeleteFilter;

  protected AbstractResourceEventSource(Class<R> resourceClass) {
    this.resourceClass = resourceClass;
  }

  @Override
  public Class<R> resourceType() {
    return resourceClass;
  }

  public void setOnAddFilter(Predicate<R> onAddFilter) {
    this.onAddFilter = onAddFilter;
  }

  public void setOnUpdateFilter(
      BiPredicate<R, R> onUpdateFilter) {
    this.onUpdateFilter = onUpdateFilter;
  }

  public void setOnDeleteFilter(
      BiPredicate<R, Boolean> onDeleteFilter) {
    this.onDeleteFilter = onDeleteFilter;
  }
}
