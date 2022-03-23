package io.javaoperatorsdk.operator.processing.dependent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationEventFilter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public abstract class AbstractDependentResource<R, P extends HasMetadata>
    implements DependentResource<R, P> {
  private static final Logger log = LoggerFactory.getLogger(AbstractDependentResource.class);

  protected final boolean creatable = this instanceof Creator;
  protected final boolean updatable = this instanceof Updater;
  protected final boolean deletable = this instanceof Deleter;
  protected Creator<R, P> creator;
  protected Updater<R, P> updater;

  @SuppressWarnings("unchecked")
  public AbstractDependentResource() {
    creator = creatable ? (Creator<R, P>) this : null;
    updater = updatable ? (Updater<R, P>) this : null;
  }

  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    var maybeActual = getResource(primary);
    if (creatable || updatable) {
      if (maybeActual.isEmpty()) {
        if (creatable) {
          var desired = desired(primary, context);
          log.info("Creating {} for primary {}", desired.getClass().getSimpleName(),
              ResourceID.fromResource(primary));
          log.debug("Creating dependent {} for primary {}", desired, primary);
          var createdResource = handleCreate(desired, primary, context);
          return ReconcileResult.resourceCreated(createdResource);
        }
      } else {
        final var actual = maybeActual.get();
        if (updatable) {
          final var match = updater.match(actual, primary, context);
          if (!match.matched()) {
            final var desired = match.computedDesired().orElse(desired(primary, context));
            log.info("Updating {} for primary {}", desired.getClass().getSimpleName(),
                ResourceID.fromResource(primary));
            log.debug("Updating dependent {} for primary {}", desired, primary);
            var updatedResource = handleUpdate(actual, desired, primary, context);
            return ReconcileResult.resourceUpdated(updatedResource);
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
    return ReconcileResult.noOperation(maybeActual.orElse(null));
  }

  protected R handleCreate(R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R created = null;
    try {
      prepareEventFiltering(desired, resourceID);
      created = creator.create(desired, primary, context);
      cacheAfterCreate(resourceID, created);
      return created;
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

  protected R handleUpdate(R actual, R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R updated = null;
    try {
      prepareEventFiltering(desired, resourceID);
      updated = updater.update(actual, desired, primary, context);
      cacheAfterUpdate(actual, resourceID, updated);
      return updated;
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

  @SuppressWarnings("unchecked")
  // this cannot be done in constructor since event source might be initialized later
  protected boolean isFilteringEventSource() {
    if (this instanceof EventSourceProvider) {
      final var eventSource = ((EventSourceProvider<P>) this).getEventSource();
      return eventSource instanceof RecentOperationEventFilter;
    } else {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  // this cannot be done in constructor since event source might be initialized later
  protected boolean isRecentOperationCacheFiller() {
    if (this instanceof EventSourceProvider) {
      final var eventSource = ((EventSourceProvider<P>) this).getEventSource();
      return eventSource instanceof RecentOperationCacheFiller;
    } else {
      return false;
    }
  }

  protected R desired(P primary, Context<P> context) {
    throw new IllegalStateException(
        "desired method must be implemented if this DependentResource can be created and/or updated");
  }
}
