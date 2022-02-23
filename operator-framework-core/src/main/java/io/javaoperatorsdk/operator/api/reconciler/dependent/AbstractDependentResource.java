package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public abstract class AbstractDependentResource<R, P extends HasMetadata>
    implements DependentResource<R, P> {

  private final boolean creatable = this instanceof Creator;
  private final boolean updatable = this instanceof Updater;
  private final boolean deletable = this instanceof Deleter;
  protected Creator<R, P> creator;
  protected Updater<R, P> updater;
  protected Deleter<P> deleter;

  public AbstractDependentResource() {
    init(Creator.NOOP, Updater.NOOP, Deleter.NOOP);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void init(Creator defaultCreator, Updater defaultUpdater, Deleter defaultDeleter) {
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
          creator.create(desired, primary, context);
        }
      } else {
        final var actual = maybeActual.get();
        if (updatable && !updater.match(actual, desired, context)) {
          updater.update(actual, desired, primary, context);
        }
      }
    }
  }

  public void update(R actual, R target, P primary, Context context) {
    updater.update(actual, target, primary, context);
  }

  public void create(R target, P primary, Context context) {
    creator.create(target, primary, context);
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
