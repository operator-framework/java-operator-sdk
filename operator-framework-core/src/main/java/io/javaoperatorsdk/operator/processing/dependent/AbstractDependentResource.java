package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.NameSetter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * An abstract implementation of {@link DependentResource} to be used as base for custom
 * implementations, providing, in particular, the core {@link #reconcile(HasMetadata, Context)}
 * logic for dependents
 *
 * @param <R> the dependent resource type
 * @param <P> the associated primary resource type
 */
@Ignore
public abstract class AbstractDependentResource<R, P extends HasMetadata>
    implements DependentResource<R, P>, NameSetter {
  private static final Logger log = LoggerFactory.getLogger(AbstractDependentResource.class);

  private final boolean creatable = this instanceof Creator;
  private final boolean updatable = this instanceof Updater;
  private final boolean deletable = this instanceof Deleter;
  private final DependentResourceReconciler<R, P> dependentResourceReconciler;
  protected Creator<R, P> creator;
  protected Updater<R, P> updater;
  protected String name;

  protected AbstractDependentResource() {
    this(null);
  }

  @SuppressWarnings("unchecked")
  protected AbstractDependentResource(String name) {
    creator = creatable ? (Creator<R, P>) this : null;
    updater = updatable ? (Updater<R, P>) this : null;

    dependentResourceReconciler =
        this instanceof BulkDependentResource
            ? new BulkDependentResourceReconciler<>((BulkDependentResource<R, P>) this)
            : new SingleDependentResourceReconciler<>(this);
    this.name = name == null ? DependentResource.defaultNameFor(this.getClass()) : name;
  }

  /**
   * Overriding classes are strongly encouraged to call this implementation as part of their
   * implementation, as they otherwise risk breaking functionality.
   *
   * @param primary the primary resource for which we want to reconcile the dependent state
   * @param context {@link Context} providing useful contextual information
   * @return the reconciliation result
   */
  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    return dependentResourceReconciler.reconcile(primary, context);
  }

  protected ReconcileResult<R> reconcile(P primary, R actualResource, Context<P> context) {
    if (creatable() || updatable()) {
      if (actualResource == null) {
        if (creatable) {
          var desired = desired(primary, context);
          throwIfNull(desired, primary, "Desired");
          logForOperation("Creating", primary, desired);
          var createdResource = handleCreate(desired, primary, context);
          return ReconcileResult.resourceCreated(createdResource);
        }
      } else {
        if (updatable()) {
          final Matcher.Result<R> match = match(actualResource, primary, context);
          if (!match.matched()) {
            final var desired = match.computedDesired().orElseGet(() -> desired(primary, context));
            throwIfNull(desired, primary, "Desired");
            logForOperation("Updating", primary, desired);
            var updatedResource = handleUpdate(actualResource, desired, primary, context);
            return ReconcileResult.resourceUpdated(updatedResource);
          } else {
            log.debug(
                "Update skipped for dependent {} as it matched the existing one",
                actualResource instanceof HasMetadata
                    ? ResourceID.fromResource((HasMetadata) actualResource)
                    : getClass().getSimpleName());
          }
        } else {
          log.debug(
              "Update skipped for dependent {} implement Updater interface to modify it",
              actualResource instanceof HasMetadata
                  ? ResourceID.fromResource((HasMetadata) actualResource)
                  : getClass().getSimpleName());
        }
      }
    } else {
      log.debug(
          "Dependent {} is read-only, implement Creator and/or Updater interfaces to modify it",
          getClass().getSimpleName());
    }
    return ReconcileResult.noOperation(actualResource);
  }

  public abstract Result<R> match(R resource, P primary, Context<P> context);

  @Override
  public Optional<R> getSecondaryResource(P primary, Context<P> context) {

    var secondaryResources = context.getSecondaryResources(resourceType());
    if (secondaryResources.isEmpty()) {
      return Optional.empty();
    } else {
      return selectTargetSecondaryResource(secondaryResources, primary, context);
    }
  }

  /**
   * Selects the actual secondary resource matching the desired state derived from the primary
   * resource when several resources of the same type are found in the context. This method allows
   * for optimized implementations in subclasses since this default implementation will check each
   * secondary candidates for equality with the specified desired state, which might end up costly.
   *
   * @param secondaryResources to select the target resource from
   * @param primary the primary resource
   * @param context the context in which this method is called
   * @return the matching secondary resource or {@link Optional#empty()} if none matches
   * @throws IllegalStateException if more than one candidate is found, in which case some other
   *     mechanism might be necessary to distinguish between candidate secondary resources
   */
  protected Optional<R> selectTargetSecondaryResource(
      Set<R> secondaryResources, P primary, Context<P> context) {
    R desired = desired(primary, context);
    var targetResources = secondaryResources.stream().filter(r -> r.equals(desired)).toList();
    if (targetResources.size() > 1) {
      throw new IllegalStateException(
          "More than one secondary resource related to primary: " + targetResources);
    }
    return targetResources.isEmpty() ? Optional.empty() : Optional.of(targetResources.get(0));
  }

  private void throwIfNull(R desired, P primary, String descriptor) {
    if (desired == null) {
      throw new DependentResourceException(
          descriptor + " cannot be null. Primary ID: " + ResourceID.fromResource(primary));
    }
  }

  private void logForOperation(String operation, P primary, R desired) {
    final var desiredDesc =
        desired instanceof HasMetadata
            ? "'"
                + ((HasMetadata) desired).getMetadata().getName()
                + "' "
                + ((HasMetadata) desired).getKind()
            : desired.getClass().getSimpleName();
    log.debug("{} {} for primary {}", operation, desiredDesc, ResourceID.fromResource(primary));
  }

  protected R handleCreate(R desired, P primary, Context<P> context) {
    R created = creator.create(desired, primary, context);
    throwIfNull(created, primary, "Created resource");
    onCreated(primary, created, context);
    return created;
  }

  /**
   * Allows subclasses to perform additional processing (e.g. caching) on the created resource if
   * needed.
   *
   * @param primary the {@link ResourceID} of the primary resource associated with the newly created
   *     resource
   * @param created the newly created resource
   * @param context the context in which this operation is called
   */
  protected abstract void onCreated(P primary, R created, Context<P> context);

  /**
   * Allows subclasses to perform additional processing on the updated resource if needed.
   *
   * @param primary the {@link ResourceID} of the primary resource associated with the newly updated
   *     resource
   * @param updated the updated resource
   * @param actual the resource as it was before the update
   * @param context the context in which this operation is called
   */
  protected abstract void onUpdated(P primary, R updated, R actual, Context<P> context);

  protected R handleUpdate(R actual, R desired, P primary, Context<P> context) {
    R updated = updater.update(actual, desired, primary, context);
    throwIfNull(updated, primary, "Updated resource");
    onUpdated(primary, updated, actual, context);
    return updated;
  }

  protected R desired(P primary, Context<P> context) {
    throw new IllegalStateException(
        "desired method must be implemented if this DependentResource can be created and/or"
            + " updated");
  }

  public void delete(P primary, Context<P> context) {
    dependentResourceReconciler.delete(primary, context);
  }

  protected void handleDelete(P primary, R secondary, Context<P> context) {
    throw new IllegalStateException(
        "handleDelete method must be implemented if Deleter trait is supported");
  }

  protected boolean isCreatable() {
    return creatable;
  }

  @SuppressWarnings("unused")
  protected boolean isUpdatable() {
    return updatable;
  }

  @Override
  public boolean isDeletable() {
    return deletable;
  }

  @Override
  public String name() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  protected boolean creatable() {
    return creatable;
  }

  protected boolean updatable() {
    return updatable;
  }
}
