package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public abstract class AbstractDependentResource<R, P extends HasMetadata>
    implements DependentResource<R, P> {

  @Override
  public void reconcile(P primary, Context context) {
    var actual = getResource(primary);
    var desired = desired(primary, context);
    if (actual.isEmpty()) {
      create(desired,primary, context);
    } else {
      if (!match(actual.get(), desired, context)) {
        update(actual.get(), desired, primary, context);
      }
    }
  }

  protected abstract R desired(P primary, Context context);

  protected abstract boolean match(R actual, R target, Context context);

  protected abstract R create(R target,P primary, Context context);

  // the actual needed to copy/preserve new labels or annotations
  protected abstract R update(R actual, R target, P primary, Context context);

}
