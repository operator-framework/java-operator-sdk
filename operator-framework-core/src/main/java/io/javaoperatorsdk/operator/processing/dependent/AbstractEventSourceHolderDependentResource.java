package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceAware;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

@Ignore
public abstract class AbstractEventSourceHolderDependentResource<R, P extends HasMetadata, T extends ResourceEventSource<R, P>>
    extends AbstractDependentResource<R, P> implements EventSourceAware<P> {

  private T eventSource;
  private final Class<R> resourceType;
  private boolean isCacheFillerEventSource;
  protected OnAddFilter<R> onAddFilter;
  protected OnUpdateFilter<R> onUpdateFilter;
  protected OnDeleteFilter<R> onDeleteFilter;
  protected GenericFilter<R> genericFilter;
  protected String eventSourceNameToUse;

  protected AbstractEventSourceHolderDependentResource(Class<R> resourceType) {
    this.resourceType = resourceType;
  }


  public Optional<ResourceEventSource<R, P>> eventSource(EventSourceContext<P> context) {
    // some sub-classes (e.g. KubernetesDependentResource) can have their event source created
    // before this method is called in the managed case, so only create the event source if it
    // hasn't already been set.
    // The filters are applied automatically only if event source is created automatically. So if an
    // event source
    // is shared between dependent resources this does not override the existing filters.
    if (eventSource == null && eventSourceNameToUse == null) {
      setEventSource(createEventSource(context));
      applyFilters();
    }
    return Optional.ofNullable(eventSource);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void selectEventSources(EventSourceRetriever<P> eventSourceRetriever) {
    if (eventSourceNameToUse != null) {
      setEventSource(
          (T) eventSourceRetriever.getResourceEventSourceFor(resourceType(), eventSourceNameToUse));
    }
  }

  /** To make this backwards compatible even for respect of overriding */
  public T initEventSource(EventSourceContext<P> context) {
    return (T) eventSource(context).orElseThrow();
  }

  @Override
  public void useEventSourceWithName(String name) {
    this.eventSourceNameToUse = name;
  }

  @Override
  public Class<R> resourceType() {
    return resourceType;
  }

  protected abstract T createEventSource(EventSourceContext<P> context);

  protected void setEventSource(T eventSource) {
    isCacheFillerEventSource = eventSource instanceof RecentOperationCacheFiller;
    this.eventSource = eventSource;
  }

  protected void applyFilters() {
    this.eventSource.setOnAddFilter(onAddFilter);
    this.eventSource.setOnUpdateFilter(onUpdateFilter);
    this.eventSource.setOnDeleteFilter(onDeleteFilter);
    this.eventSource.setGenericFilter(genericFilter);
  }

  public Optional<ResourceEventSource<R, P>> eventSource() {
    return Optional.ofNullable(eventSource);
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

  public AbstractEventSourceHolderDependentResource<R, P, T> setEventSourceNameToUse(
      String eventSourceNameToUse) {
    this.eventSourceNameToUse = eventSourceNameToUse;
    return this;
  }
}
