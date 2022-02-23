package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public abstract class AbstractDependentResource<R, P extends HasMetadata>
    implements DependentResource<R, P> {

  protected Creator<R, P> creator;
  protected Updater<R, P> updater;
  protected Deleter<P> deleter;

  public AbstractDependentResource() {
    init(Creator.NOOP, Updater.NOOP, Deleter.NOOP);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void init(Creator defaultCreator, Updater defaultUpdater, Deleter defaultDeleter) {
    creator = this instanceof Creator ? (Creator<R, P>) this : defaultCreator;
    updater = this instanceof Updater ? (Updater<R, P>) this : defaultUpdater;
    deleter = this instanceof Deleter ? (Deleter<P>) this : defaultDeleter;
  }

  @Override
  public void reconcile(P primary, Context context) {
    final var creatable = isCreatable(primary);
    final var updatable = isUpdatable(primary);
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
    if (deleter != Deleter.NOOP) {
      deleter.delete(primary, context);
    }
  }

  protected R desired(P primary, Context context) {
    throw new IllegalStateException(
        "desired method must be implemented if this DependentResource can be created and/or updated");
  }

  @SuppressWarnings("unused")
  protected boolean isCreatable(P primary) {
    return creator != Creator.NOOP;
  }

  @SuppressWarnings("unused")
  protected boolean isUpdatable(P primary) {
    return updater != Updater.NOOP;
  }
}
