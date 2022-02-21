package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public abstract class AbstractDependentResource<R, P extends HasMetadata>
    implements DependentResource<R, P> {

  private final Creator<R, P> creator;
  private final Updater<R, P> updater;
  private final Matcher<R> matcher;

  @SuppressWarnings("unchecked")
  public AbstractDependentResource() {
    creator = this instanceof Creator ? (Creator<R, P>) this : Creator.NOOP;
    updater = this instanceof Updater ? (Updater<R, P>) this : Updater.NOOP;
    matcher = this instanceof Matcher ? (Matcher<R>) this : Matcher.DEFAULT;
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
        if (updatable && !matcher.match(actual, desired, context)) {
          updater.update(actual, desired, primary, context);
        }
      }
    }
  }

  protected abstract R desired(P primary, Context context);

  @SuppressWarnings("unused")
  protected boolean isCreatable(P primary) {
    return creator != Creator.NOOP;
  }

  @SuppressWarnings("unused")
  protected boolean isUpdatable(P primary) {
    return updater != Updater.NOOP;
  }
}
