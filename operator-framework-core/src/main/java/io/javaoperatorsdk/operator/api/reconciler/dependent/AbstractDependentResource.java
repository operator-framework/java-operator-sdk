package io.javaoperatorsdk.operator.api.reconciler.dependent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public abstract class AbstractDependentResource<R, P extends HasMetadata>
    implements DependentResource<R, P> {
  private static final Logger log = LoggerFactory.getLogger(AbstractDependentResource.class);

  private final boolean creatable = this instanceof Creator;
  private final boolean updatable = this instanceof Updater;
  private final boolean deletable = this instanceof Deleter;
  protected Creator<R, P> creator;
  protected Updater<R, P> updater;
  protected Deleter<P> deleter;

  @SuppressWarnings("unchecked")
  public AbstractDependentResource() {
    creator = creatable ? (Creator<R, P>) this : null;
    updater = updatable ? (Updater<R, P>) this : null;
    deleter = deletable ? (Deleter<P>) this : null;
  }

  @Override
  public void reconcile(P primary, Context<P> context) {
    final var creatable = isCreatable(primary, context);
    final var updatable = isUpdatable(primary, context);
    if (creatable || updatable) {
      var maybeActual = getResource(primary);
      if (maybeActual.isEmpty()) {
        if (creatable) {
          var desired = desired(primary, context);
          log.debug("Creating dependent {} for primary {}", desired, primary);
          handleCreate(desired, primary, context);
        }
      } else {
        final var actual = maybeActual.get();
        if (updatable) {
          final var match = updater.match(actual, primary, context);
          if (!match.matched()) {
            final var desired = match.computedDesired().orElse(desired(primary, context));
            log.debug("Updating dependent {} for primary {}", desired, primary);
            handleUpdate(actual, desired, primary, context);
          }
        } else {
          log.debug("Update skipped for dependent {} as it matched the existing one", actual);
        }
      }
    } else {
      log.debug(
          "Dependent {} is read-only, implement Creator and/or Updater interfaces to modify it",
          getClass().getSimpleName());
    }
  }

  protected void handleCreate(R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R created = null;
    try {
      prepareEventFiltering(desired, resourceID);
      created = creator.create(desired, primary, context);
      cacheAfterCreate(resourceID, created);
    } catch (RuntimeException e) {
      cleanupAfterEventFiltering(desired, resourceID, created);
      throw e;
    }
  }

  private void cleanupAfterEventFiltering(R desired, ResourceID resourceID, R created) {
    if (isFilteringEventSource()) {
      eventSourceAsRecentOperationEventFilter()
          .cleanupOnCreateOrUpdateEventFiltering(resourceID, created);
    }
  }

  private void cacheAfterCreate(ResourceID resourceID, R created) {
    if (isRecentOperationCacheFiller()) {
      eventSourceAsRecentOperationCacheFiller().handleRecentResourceCreate(resourceID, created);
    }
  }

  private void cacheAfterUpdate(R actual, ResourceID resourceID, R updated) {
    if (isRecentOperationCacheFiller()) {
      eventSourceAsRecentOperationCacheFiller().handleRecentResourceUpdate(resourceID, updated,
          actual);
    }
  }

  private void prepareEventFiltering(R desired, ResourceID resourceID) {
    if (isFilteringEventSource()) {
      eventSourceAsRecentOperationEventFilter().prepareForCreateOrUpdateEventFiltering(resourceID,
          desired);
    }
  }

  protected void handleUpdate(R actual, R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R updated = null;
    try {
      prepareEventFiltering(desired, resourceID);
      updated = updater.update(actual, desired, primary, context);
      cacheAfterUpdate(actual, resourceID, updated);
    } catch (RuntimeException e) {
      cleanupAfterEventFiltering(desired, resourceID, updated);
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  private RecentOperationEventFilter<R> eventSourceAsRecentOperationEventFilter() {
    return (RecentOperationEventFilter<R>) ((EventSourceProvider<P>) this).getEventSource();
  }

  @SuppressWarnings("unchecked")
  private RecentOperationCacheFiller<R> eventSourceAsRecentOperationCacheFiller() {
    return (RecentOperationCacheFiller<R>) ((EventSourceProvider<P>) this).getEventSource();
  }

  // this cannot be done in constructor since event source might be initialized later
  protected boolean isFilteringEventSource() {
    if (this instanceof EventSourceProvider) {
      final var eventSource = ((EventSourceProvider<P>) this).getEventSource();
      return eventSource instanceof RecentOperationEventFilter;
    } else {
      return false;
    }
  }

  // this cannot be done in constructor since event source might be initialized later
  protected boolean isRecentOperationCacheFiller() {
    if (this instanceof EventSourceProvider) {
      final var eventSource = ((EventSourceProvider<P>) this).getEventSource();
      return eventSource instanceof RecentOperationCacheFiller;
    } else {
      return false;
    }
  }

  @Override
  public void cleanup(P primary, Context<P> context) {
    if (isDeletable(primary, context)) {
      deleter.delete(primary, context);
    }
  }

  protected R desired(P primary, Context<P> context) {
    throw new IllegalStateException(
        "desired method must be implemented if this DependentResource can be created and/or updated");
  }

  @SuppressWarnings("unused")
  protected boolean isCreatable(P primary, Context<P> context) {
    return creatable;
  }

  @SuppressWarnings("unused")
  protected boolean isUpdatable(P primary, Context<P> context) {
    return updatable;
  }

  @SuppressWarnings("unused")
  protected boolean isDeletable(P primary, Context<P> context) {
    return deletable;
  }
}
