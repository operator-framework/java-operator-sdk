package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface GenericDependentResource<R, P extends HasMetadata>
    extends DependentResource<R, P> {


  default void reconcile(P primary, Context context) {
    var actual = getResource(primary);
    var desired = desired(primary, context);
    if (actual.isEmpty()) {
      create(desired, primary, context);
    } else {
      if (!match(actual.get(), desired, context)) {
        update(actual.get(), desired, primary, context);
      }
    }
  }

  R desired(P primary, Context context);

  boolean match(R actual, R target, Context context);

  void create(R target, P primary, Context context);

  // the actual needed to copy/preserve new labels or annotations
  void update(R actual, R target, P primary, Context context);

}
