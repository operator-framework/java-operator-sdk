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

  protected OnAddFilter<? super R> onAddFilter;
  protected OnUpdateFilter<? super R> onUpdateFilter;
  protected OnDeleteFilter<? super R> onDeleteFilter;
  protected GenericFilter<? super R> genericFilter;

  private final String name;

  protected AbstractResourceEventSource(Class<R> resourceClass) {
    this(resourceClass, resourceClass.getName());
  }

  protected AbstractResourceEventSource(Class<R> resourceClass, String name) {
    this.resourceClass = resourceClass;
    this.name = name == null ? resourceClass.getName() : name;
  }

  @Override
  public Class<R> resourceType() {
    return resourceClass;
  }

  public void setOnAddFilter(OnAddFilter<? super R> onAddFilter) {
    this.onAddFilter = onAddFilter;
  }

  public void setOnUpdateFilter(
      OnUpdateFilter<? super R> onUpdateFilter) {
    this.onUpdateFilter = onUpdateFilter;
  }

  public void setOnDeleteFilter(
      OnDeleteFilter<? super R> onDeleteFilter) {
    this.onDeleteFilter = onDeleteFilter;
  }

  public void setGenericFilter(GenericFilter<? super R> genericFilter) {
    this.genericFilter = genericFilter;
  }

  @Override
  public String name() {
    return name;
  }
}
