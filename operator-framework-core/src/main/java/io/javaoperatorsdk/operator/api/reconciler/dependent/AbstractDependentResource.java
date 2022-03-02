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
    init(Creator.NOOP, Updater.NOOP, Deleter.NOOP);
  }

  @SuppressWarnings({"unchecked"})
  protected void init(Creator<R, P> defaultCreator, Updater<R, P> defaultUpdater,
      Deleter<P> defaultDeleter) {
    creator = creatable ? (Creator<R, P>) this : defaultCreator;
    updater = updatable ? (Updater<R, P>) this : defaultUpdater;
    deleter = deletable ? (Deleter<P>) this : defaultDeleter;
  }

  @Override
  public void reconcile(P primary, Context context) {
    final var creatable = isCreatable(primary, context);
    final var updatable = isUpdatable(primary, context);
    if (creatable || updatable) {
      var maybeActual = getResource(primary);
      var desired = desired(primary, context);
      if (maybeActual.isEmpty()) {
        if (creatable) {
          log.debug("Creating dependent {} for primary {}", desired, primary);
          handleCreate(desired, primary, context);
        }
      } else {
        final var actual = maybeActual.get();
        if (updatable && !updater.match(actual, desired, context)) {
          log.debug("Updating dependent {} for primary {}", desired, primary);
          handleUpdate(actual, desired, primary, context);
        } else {
          log.debug("Update skipped for dependent {} as it matched the existing one", desired);
        }
      }
    } else {
      log.debug(
          "Dependent {} is read-only, implement Creator and/or Updater interfaces to modify it",
          getClass().getSimpleName());
    }
  }

  protected R handleCreate(R desired, P primary, Context context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R created = null;
    try {
      if (isRecentOperationEventFilter()) {
        getRecentOperationEventFilter().prepareForCreateOrUpdateEventFiltering(resourceID, desired);
      }
      created = creator.create(desired, primary, context);
      if (isRecentOperationCacheFiller()) {
        getRecentOperationCacheFiller().handleRecentResourceCreate(resourceID, created);
      }
      return created;
    } catch (RuntimeException e) {
      if (isRecentOperationEventFilter()) {
        getRecentOperationEventFilter()
            .cleanupOnCreateOrUpdateEventFiltering(resourceID, created == null ? desired : created);
      }
      throw e;
    }
  }

  protected R handleUpdate(R actual, R desired, P primary, Context context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R updated = null;
    try {
      if (isRecentOperationEventFilter()) {
        getRecentOperationEventFilter().prepareForCreateOrUpdateEventFiltering(resourceID, desired);
      }
      updated = updater.update(actual, desired, primary, context);
      if (isRecentOperationCacheFiller()) {
        getRecentOperationCacheFiller().handleRecentResourceUpdate(resourceID, updated, actual);
      }
      return updated;
    } catch (RuntimeException e) {
      if (isRecentOperationEventFilter()) {
        getRecentOperationEventFilter()
            .cleanupOnCreateOrUpdateEventFiltering(resourceID, updated == null ? desired : updated);
      }
      throw e;
    }
  }

  private boolean isRecentOperationEventFilter() {
    return this instanceof EventSourceProvider &&
        ((EventSourceProvider<P>) this).getEventSource() instanceof RecentOperationEventFilter;
  }

  private RecentOperationEventFilter<R> getRecentOperationEventFilter() {
    return (RecentOperationEventFilter<R>) ((EventSourceProvider<P>) this).getEventSource();
  }

  private boolean isRecentOperationCacheFiller() {
    return this instanceof RecentOperationCacheFiller &&
        ((EventSourceProvider<P>) this).getEventSource() instanceof RecentOperationCacheFiller;
  }

  private RecentOperationCacheFiller<R> getRecentOperationCacheFiller() {
    return (RecentOperationCacheFiller<R>) ((EventSourceProvider<P>) this).getEventSource();
  }

  @Override
  public void delete(P primary, Context context) {
    if (isDeletable(primary, context)) {
      deleter.delete(primary, context);
    }
  }

  protected R desired(P primary, Context context) {
    throw new IllegalStateException(
        "desired method must be implemented if this DependentResource can be created and/or updated");
  }

  @SuppressWarnings("unused")
  protected boolean isCreatable(P primary, Context context) {
    return creatable;
  }

  @SuppressWarnings("unused")
  protected boolean isUpdatable(P primary, Context context) {
    return updatable;
  }

  @SuppressWarnings("unused")
  protected boolean isDeletable(P primary, Context context) {
    return deletable;
  }
}
