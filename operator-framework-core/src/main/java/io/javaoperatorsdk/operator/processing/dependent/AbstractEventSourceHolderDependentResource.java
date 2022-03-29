package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationEventFilter;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

public abstract class AbstractEventSourceHolderDependentResource<R, P extends HasMetadata, T extends ResourceEventSource<R, P>>
    extends AbstractDependentResource<R, P>
    implements EventSourceProvider<P> {
  private T eventSource;
  private boolean isFilteringEventSource;
  private boolean isCacheFillerEventSource;

  public EventSource initEventSource(EventSourceContext<P> context) {
    // some sub-classes (e.g. KubernetesDependentResource) can have their event source created
    // before this method is called in the managed case, so only create the event source if it
    // hasn't already been set
    if (eventSource == null) {
      eventSource = createEventSource(context);
    }

    // but we still need to record which interfaces the event source implements even if it has
    // already been set before this method is called
    isFilteringEventSource = eventSource instanceof RecentOperationEventFilter;
    isCacheFillerEventSource = eventSource instanceof RecentOperationCacheFiller;
    return eventSource;
  }

  protected abstract T createEventSource(EventSourceContext<P> context);

  protected void setEventSource(T eventSource) {
    this.eventSource = eventSource;
  }

  protected T eventSource() {
    return eventSource;
  }

  protected R handleCreate(R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R created = null;
    try {
      prepareEventFiltering(desired, resourceID);
      created = super.handleCreate(desired, primary, context);
      return created;
    } catch (RuntimeException e) {
      cleanupAfterEventFiltering(desired, resourceID, created);
      throw e;
    }
  }

  protected R handleUpdate(R actual, R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R updated = null;
    try {
      prepareEventFiltering(desired, resourceID);
      updated = super.handleUpdate(actual, desired, primary, context);
      return updated;
    } catch (RuntimeException e) {
      cleanupAfterEventFiltering(desired, resourceID, updated);
      throw e;
    }
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

  private void prepareEventFiltering(R desired, ResourceID resourceID) {
    if (isFilteringEventSource) {
      recentOperationEventFilter().prepareForCreateOrUpdateEventFiltering(resourceID, desired);
    }
  }

  private void cleanupAfterEventFiltering(R desired, ResourceID resourceID, R created) {
    if (isFilteringEventSource) {
      recentOperationEventFilter().cleanupOnCreateOrUpdateEventFiltering(resourceID, created);
    }
  }

  @SuppressWarnings("unchecked")
  private RecentOperationEventFilter<R> recentOperationEventFilter() {
    return (RecentOperationEventFilter<R>) eventSource;
  }

  @SuppressWarnings("unchecked")
  private RecentOperationCacheFiller<R> recentOperationCacheFiller() {
    return (RecentOperationCacheFiller<R>) eventSource;
  }
}
