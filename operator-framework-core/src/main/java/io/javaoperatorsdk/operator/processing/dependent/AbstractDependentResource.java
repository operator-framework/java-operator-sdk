package io.javaoperatorsdk.operator.processing.dependent;

import java.util.*;

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

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected AbstractDependentResource() {
    creator = creatable ? (Creator<R, P>) this : null;
    updater = updatable ? (Updater<R, P>) this : null;

    bulkDependentResource = bulk ? (BulkDependentResource) this : null;
  }


  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    if (bulk) {
      final var targetResources = bulkDependentResource.desiredResources(primary, context);

      Map<String, R> actualResources =
          bulkDependentResource.getSecondaryResources(primary, context);

      deleteBulkResourcesIfRequired(targetResources.keySet(), actualResources, primary, context);
      final List<ReconcileResult<R>> results = new ArrayList<>(targetResources.size());

      targetResources.forEach((key, resource) -> {
        results.add(reconcileIndexAware(primary, actualResources.get(key), resource, key, context));
      });

      return ReconcileResult.aggregatedResult(results);
    } else {
      var actualResource = getSecondaryResource(primary, context);
      return reconcileIndexAware(primary, actualResource.orElse(null), null, null, context);
    }
  }

  @SuppressWarnings({"rawtypes"})
  protected void deleteBulkResourcesIfRequired(Set targetKeys, Map<String, R> actualResources,
      P primary, Context<P> context) {
    actualResources.forEach((key, value) -> {
      if (!targetKeys.contains(key)) {
        bulkDependentResource.deleteBulkResource(primary, value, key, context);
      }
    });
  }

  protected ReconcileResult<R> reconcileIndexAware(P primary, R actualResource, R desiredResource,
      String key,
      Context<P> context) {
    if (creatable || updatable) {
      if (actualResource == null) {
        if (creatable) {
          var desired = bulkAwareDesired(primary, desiredResource, context);
          throwIfNull(desired, primary, "Desired");
          logForOperation("Creating", primary, desired);
          var createdResource = handleCreate(desired, primary, context);
          return ReconcileResult.resourceCreated(createdResource);
        }
      } else {
        if (updatable) {
          final Matcher.Result<R> match;
          if (bulk) {
            match =
                bulkDependentResource.match(actualResource, desiredResource, primary, key, context);
          } else {
            match = updater.match(actualResource, primary, context);
          }
          if (!match.matched()) {
            final var desired =
                match.computedDesired().orElse(bulkAwareDesired(primary, desiredResource, context));
            throwIfNull(desired, primary, "Desired");
            logForOperation("Updating", primary, desired);
            var updatedResource = handleUpdate(actualResource, desired, primary, context);
            return ReconcileResult.resourceUpdated(updatedResource);
          }
        } else {
          log.debug("Update skipped for dependent {} as it matched the existing one",
              actualResource);
        }
      }
    } else {
      log.debug(
          "Dependent {} is read-only, implement Creator and/or Updater interfaces to modify it",
          getClass().getSimpleName());
    }
    return ReconcileResult.noOperation(actualResource);
  }

  private R bulkAwareDesired(P primary, R alreadyComputedDesire, Context<P> context) {
    return bulk ? alreadyComputedDesire
        : desired(primary, context);
  }

  @Override
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

  public void delete(P primary, Context<P> context) {
    if (bulk) {
      var actualResources = bulkDependentResource.getSecondaryResources(primary, context);
      deleteBulkResourcesIfRequired(Collections.emptySet(), actualResources, primary, context);
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
