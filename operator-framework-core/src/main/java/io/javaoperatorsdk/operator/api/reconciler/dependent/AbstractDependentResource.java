package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public abstract class AbstractDependentResource<R, P extends HasMetadata, C>
    implements DependentResource<R, P> {

  @Override
  public void reconcile(P primary, Context context) {
    var maybeActual = getResource(primary);
    var maybeDesired = desired(primary, context);
    maybeDesired.ifPresent(desired -> {
      if (maybeActual.isEmpty()) {
        create(desired, primary, context);
      } else {
        final var actual = maybeActual.get();
        if (!match(actual, desired, context)) {
          update(actual, desired, primary, context);
        }
      }
    });
  }

  protected abstract Optional<R> desired(P primary, Context context);

  protected abstract boolean match(R actual, R target, Context context);

  protected abstract R create(R target, P primary, Context context);

  // the actual needed to copy/preserve new labels or annotations
  protected abstract R update(R actual, R target, P primary, Context context);

}
