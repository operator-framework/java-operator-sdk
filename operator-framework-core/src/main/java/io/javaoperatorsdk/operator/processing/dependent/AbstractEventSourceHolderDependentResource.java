package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationEventFilter;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

public abstract class AbstractEventSourceHolderDependentResource<R, P extends HasMetadata, T extends ResourceEventSource<R, P>>
    extends AbstractDependentResource<R, P>
    implements ResourceEventSource<R, P>, EventSourceProvider<P> {
  private T eventSource;
  private boolean isFilteringEventSource;
  private boolean isCacheFillerEventSource;

  @Override
  public void start() throws OperatorException {
    eventSource.start();
  }

  @Override
  public void stop() throws OperatorException {
    eventSource.stop();
  }

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
    return this;
  }

  protected abstract T createEventSource(EventSourceContext<P> context);

  protected void setEventSource(T eventSource) {
    this.eventSource = eventSource;
  }

  @Override
  public void setEventHandler(EventHandler handler) {
    eventSource.setEventHandler(handler);
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


  protected void cacheAfterCreate(ResourceID resourceID, R created) {
    if (isCacheFillerEventSource) {
      recentOperationCacheFiller().handleRecentResourceCreate(resourceID, created);
    }
  }

  protected void cacheAfterUpdate(R actual, ResourceID resourceID, R updated) {
    if (isCacheFillerEventSource) {
      recentOperationCacheFiller().handleRecentResourceUpdate(resourceID, updated, actual);
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
