package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@Ignore
public abstract class AbstractDependentResource<R, P extends HasMetadata>
    implements DependentResource<R, P> {
  private static final Logger log = LoggerFactory.getLogger(AbstractDependentResource.class);

  private final boolean creatable = this instanceof Creator;
  private final boolean updatable = this instanceof Updater;
  private final boolean bulk = this instanceof BulkDependentResource;

  protected Creator<R, P> creator;
  protected Updater<R, P> updater;
  protected BulkDependentResource<R, P> bulkDependentResource;
  private ResourceDiscriminator<R, P> resourceDiscriminator;
  private int currentCount;

  @SuppressWarnings("unchecked")
  public AbstractDependentResource() {
    creator = creatable ? (Creator<R, P>) this : null;
    updater = updatable ? (Updater<R, P>) this : null;

    bulkDependentResource = bulk ? (BulkDependentResource<R, P>) this : null;
  }

  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    if (bulk) {
      final var count = bulkDependentResource.count(primary, context);
      deleteBulkResourcesIfRequired(count, primary, context);
      @SuppressWarnings("unchecked")
      final ReconcileResult<R>[] results = new ReconcileResult[count];
      for (int i = 0; i < count; i++) {
        results[i] = reconcileIndexAware(primary, i, context);
      }
      currentCount = count;
      return ReconcileResult.aggregatedResult(results);
    } else {
      return reconcileIndexAware(primary, 0, context);
    }
  }

  protected void deleteBulkResourcesIfRequired(int targetCount, P primary, Context<P> context) {
    if (targetCount >= currentCount) {
      return;
    }
    for (int i = targetCount; i < currentCount; i++) {
      var resource = bulkDependentResource.getSecondaryResource(primary, i, context);
      var index = i;
      resource.ifPresent(
          r -> bulkDependentResource.deleteBulkResourceWithIndex(primary, r, index, context));
    }
  }

  protected ReconcileResult<R> reconcileIndexAware(P primary, int i, Context<P> context) {
    Optional<R> maybeActual = bulk ? bulkDependentResource.getSecondaryResource(primary, i, context)
        : getSecondaryResource(primary, context);
    if (creatable || updatable) {
      if (maybeActual.isEmpty()) {
        if (creatable) {
          var desired = desiredIndexAware(primary, i, context);
          throwIfNull(desired, primary, "Desired");
          logForOperation("Creating", primary, desired);
          var createdResource = handleCreate(desired, primary, context);
          return ReconcileResult.resourceCreated(createdResource);
        }
      } else {
        final var actual = maybeActual.get();
        if (updatable) {
          final Matcher.Result<R> match;
          if (bulk) {
            match = bulkDependentResource.match(actual, primary, i, context);
          } else {
            match = updater.match(actual, primary, context);
          }
          if (!match.matched()) {
            final var desired =
                match.computedDesired().orElse(desiredIndexAware(primary, i, context));
            throwIfNull(desired, primary, "Desired");
            logForOperation("Updating", primary, desired);
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

  private R desiredIndexAware(P primary, int i, Context<P> context) {
    return bulk ? desired(primary, i, context) : desired(primary, context);
  }

  public Optional<R> getSecondaryResource(P primary, Context<P> context) {
    return resourceDiscriminator == null ? context.getSecondaryResource(resourceType())
        : resourceDiscriminator.distinguish(resourceType(), primary, context);
  }

  private void throwIfNull(R desired, P primary, String descriptor) {
    if (desired == null) {
      throw new DependentResourceException(
          descriptor + " cannot be null. Primary ID: " + ResourceID.fromResource(primary));
    }
  }

  private void logForOperation(String operation, P primary, R desired) {
    final var desiredDesc = desired instanceof HasMetadata
        ? "'" + ((HasMetadata) desired).getMetadata().getName() + "' "
            + ((HasMetadata) desired).getKind()
        : desired.getClass().getSimpleName();
    log.info("{} {} for primary {}", operation, desiredDesc, ResourceID.fromResource(primary));
    log.debug("{} dependent {} for primary {}", operation, desired, primary);
  }

  protected R handleCreate(R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R created = creator.create(desired, primary, context);
    throwIfNull(created, primary, "Created resource");
    onCreated(resourceID, created);
    return created;
  }

  /**
   * Allows subclasses to perform additional processing (e.g. caching) on the created resource if
   * needed.
   *
   * @param primaryResourceId the {@link ResourceID} of the primary resource associated with the
   *        newly created resource
   * @param created the newly created resource
   */
  protected abstract void onCreated(ResourceID primaryResourceId, R created);

  /**
   * Allows subclasses to perform additional processing on the updated resource if needed.
   *
   * @param primaryResourceId the {@link ResourceID} of the primary resource associated with the
   *        newly updated resource
   * @param updated the updated resource
   * @param actual the resource as it was before the update
   */
  protected abstract void onUpdated(ResourceID primaryResourceId, R updated, R actual);

  protected R handleUpdate(R actual, R desired, P primary, Context<P> context) {
    ResourceID resourceID = ResourceID.fromResource(primary);
    R updated = updater.update(actual, desired, primary, context);
    throwIfNull(updated, primary, "Updated resource");
    onUpdated(resourceID, updated, actual);
    return updated;
  }

  protected R desired(P primary, Context<P> context) {
    throw new IllegalStateException(
        "desired method must be implemented if this DependentResource can be created and/or updated");
  }

  protected R desired(P primary, int index, Context<P> context) {
    throw new IllegalStateException("Must be implemented for bulk DependentResource creation");
  }

  public void delete(P primary, Context<P> context) {
    if (bulk) {
      deleteBulkResourcesIfRequired(0, primary, context);
    } else {
      handleDelete(primary, context);
    }
  }

  protected void handleDelete(P primary, Context<P> context) {
    throw new IllegalStateException("delete method be implemented if Deleter trait is supported");
  }

  public void setResourceDiscriminator(
      ResourceDiscriminator<R, P> resourceDiscriminator) {
    this.resourceDiscriminator = resourceDiscriminator;
  }

  protected boolean isCreatable() {
    return creatable;
  }

  protected boolean isUpdatable() {
    return updatable;
  }

  protected boolean isBulk() {
    return bulk;
  }
}
