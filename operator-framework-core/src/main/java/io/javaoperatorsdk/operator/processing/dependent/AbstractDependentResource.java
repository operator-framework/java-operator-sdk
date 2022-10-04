package io.javaoperatorsdk.operator.processing.dependent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

@Ignore
public abstract class AbstractDependentResource<R, P extends HasMetadata>
    implements DependentResource<R, P> {
  private static final Logger log = LoggerFactory.getLogger(AbstractDependentResource.class);

  protected final boolean creatable = this instanceof Creator;
  protected final boolean updatable = this instanceof Updater;
  protected final boolean bulk = this instanceof BulkDependentResource;

  protected Creator<R, P> creator;
  protected Updater<R, P> updater;
  protected BulkDependentResource<R, P> bulkDependentResource;
  private boolean returnEventSource = true;

  protected List<ResourceDiscriminator<R, P>> resourceDiscriminator = new ArrayList<>(1);

  @SuppressWarnings("unchecked")
  public AbstractDependentResource() {
    creator = creatable ? (Creator<R, P>) this : null;
    updater = updatable ? (Updater<R, P>) this : null;

    bulkDependentResource = bulk ? (BulkDependentResource<R, P>) this : null;
  }

  @Override
  public void doNotProvideEventSource() {
    this.returnEventSource = false;
  }

  @Override
  public Optional<ResourceEventSource<R, P>> eventSource(EventSourceContext<P> eventSourceContext) {
    if (!returnEventSource) {
      return Optional.empty();
    } else {
      return Optional.of(provideEventSource(eventSourceContext));
    }
  }

  protected abstract ResourceEventSource<R, P> provideEventSource(
      EventSourceContext<P> eventSourceContext);

  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    if (bulk) {
      final var count = bulkDependentResource.count(primary, context);
      deleteBulkResourcesIfRequired(count, lastKnownBulkSize(), primary, context);
      adjustDiscriminators(count);
      @SuppressWarnings("unchecked")
      final ReconcileResult<R>[] results = new ReconcileResult[count];
      for (int i = 0; i < count; i++) {
        results[i] = reconcileIndexAware(primary, i, context);
      }
      return ReconcileResult.aggregatedResult(results);
    } else {
      return reconcileIndexAware(primary, 0, context);
    }
  }

  protected void deleteBulkResourcesIfRequired(int targetCount, int actualCount, P primary,
      Context<P> context) {
    if (targetCount >= actualCount) {
      return;
    }
    for (int i = targetCount; i < actualCount; i++) {
      var resource = getSecondaryResourceIndexAware(primary, i, context);
      var index = i;
      resource.ifPresent(
          r -> bulkDependentResource.deleteBulkResourceWithIndex(primary, r, index, context));
    }
  }

  private void adjustDiscriminators(int count) {
    if (resourceDiscriminator.size() == count) {
      return;
    }
    if (resourceDiscriminator.size() < count) {
      for (int i = resourceDiscriminator.size(); i < count; i++) {
        resourceDiscriminator.add(bulkDependentResource.getResourceDiscriminator(i));
      }
    }
    if (resourceDiscriminator.size() > count) {
      resourceDiscriminator.subList(count, resourceDiscriminator.size()).clear();
    }
  }

  protected ReconcileResult<R> reconcileIndexAware(P primary, int i, Context<P> context) {
    Optional<R> maybeActual = bulk ? getSecondaryResourceIndexAware(primary, i, context)
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
            match = updater.match(actual, primary, i, context);
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
    return bulk ? desired(primary, i, context)
        : desired(primary, context);
  }

  public Optional<R> getSecondaryResource(P primary, Context<P> context) {
    return resourceDiscriminator.isEmpty() ? context.getSecondaryResource(resourceType())
        : resourceDiscriminator.get(0).distinguish(resourceType(), primary, context);
  }

  protected Optional<R> getSecondaryResourceIndexAware(P primary, int index, Context<P> context) {
    return context.getSecondaryResource(resourceType(), resourceDiscriminator.get(index));
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
    throw new IllegalStateException(
        "Must be implemented for bulk DependentResource creation");
  }

  public AbstractDependentResource<R, P> setResourceDiscriminator(
      ResourceDiscriminator<R, P> resourceDiscriminator) {
    if (resourceDiscriminator != null) {
      this.resourceDiscriminator.add(resourceDiscriminator);
    }
    return this;
  }

  public ResourceDiscriminator<R, P> getResourceDiscriminator() {
    if (this.resourceDiscriminator.isEmpty()) {
      return null;
    } else {
      return this.resourceDiscriminator.get(0);
    }
  }

  protected int lastKnownBulkSize() {
    return resourceDiscriminator.size();
  }

  protected boolean getReturnEventSource() {
    return returnEventSource;
  }
}
