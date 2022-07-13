package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public abstract class AbstractResourceEventSource<R, P extends HasMetadata>
    extends AbstractEventSource
    implements ResourceEventSource<R, P> {
  private final Class<R> resourceClass;

  protected OnAddFilter<R> onAddFilter;
  protected OnUpdateFilter<R> onUpdateFilter;
  protected OnDeleteFilter<R> onDeleteFilter;
  protected GenericFilter<R> genericFilter;

  protected AbstractResourceEventSource(Class<R> resourceClass) {
    this.resourceClass = resourceClass;
  }

  @Override
  public Class<R> resourceType() {
    return resourceClass;
  }

  public void setOnAddFilter(OnAddFilter<R> onAddFilter) {
    this.onAddFilter = onAddFilter;
  }

  public void setOnUpdateFilter(
      OnUpdateFilter<R> onUpdateFilter) {
    this.onUpdateFilter = onUpdateFilter;
  }

  public void setOnDeleteFilter(
      OnDeleteFilter<R> onDeleteFilter) {
    this.onDeleteFilter = onDeleteFilter;
  }

  public void setGenericFilter(GenericFilter<R> genericFilter) {
    this.genericFilter = genericFilter;
  }
}
