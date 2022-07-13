package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

@Ignore
public abstract class AbstractEventSourceHolderDependentResource<R, P extends HasMetadata, T extends ResourceEventSource<R, P>>
    extends AbstractDependentResource<R, P>
    implements EventSourceProvider<P> {

  private T eventSource;
  private boolean isCacheFillerEventSource;
  protected OnAddFilter<R> onAddFilter;
  protected OnUpdateFilter<R> onUpdateFilter;
  protected OnDeleteFilter<R> onDeleteFilter;
  protected GenericFilter<R> genericFilter;


  public EventSource initEventSource(EventSourceContext<P> context) {
    // some sub-classes (e.g. KubernetesDependentResource) can have their event source created
    // before this method is called in the managed case, so only create the event source if it
    // hasn't already been set.
    // The filters are applied automatically only if event source is created automatically. So if an
    // event source
    // is shared between dependent resources this does not override the existing filters.
    if (eventSource == null) {
      eventSource = createEventSource(context);
      applyFilters();
    }

    isCacheFillerEventSource = eventSource instanceof RecentOperationCacheFiller;
    return eventSource;
  }

  protected abstract T createEventSource(EventSourceContext<P> context);

  protected void setEventSource(T eventSource) {
    this.eventSource = eventSource;
  }

  protected void applyFilters() {
    this.eventSource.setOnAddFilter(onAddFilter);
    this.eventSource.setOnUpdateFilter(onUpdateFilter);
    this.eventSource.setOnDeleteFilter(onDeleteFilter);
    this.eventSource.setGenericFilter(genericFilter);
  }

  protected T eventSource() {
    return eventSource;
  }

  protected void onCreated(ResourceID primaryResourceId, R created) {
    if (isCacheFillerEventSource) {
      recentOperationCacheFiller().handleRecentResourceCreate(primaryResourceId, created);
    }
  }

  protected void onUpdated(ResourceID primaryResourceId, R updated, R actual) {
    if (isCacheFillerEventSource) {
      recentOperationCacheFiller().handleRecentResourceUpdate(primaryResourceId, updated, actual);
    }
  }

  @SuppressWarnings("unchecked")
  private RecentOperationCacheFiller<R> recentOperationCacheFiller() {
    return (RecentOperationCacheFiller<R>) eventSource;
  }

  public void setOnAddFilter(OnAddFilter<R> onAddFilter) {
    this.onAddFilter = onAddFilter;
  }

  public void setOnUpdateFilter(OnUpdateFilter<R> onUpdateFilter) {
    this.onUpdateFilter = onUpdateFilter;
  }

  public void setOnDeleteFilter(OnDeleteFilter<R> onDeleteFilter) {
    this.onDeleteFilter = onDeleteFilter;
  }
}
