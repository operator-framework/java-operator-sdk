package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Matcher.Result;

@SuppressWarnings("rawtypes")
public interface Updater<R, P extends HasMetadata> {
  Updater NOOP = new Updater() {
    @Override
    public Object update(Object actual, Object desired, HasMetadata primary, Context context) {
      return null;
    }

    @Override
    public Result match(Object actualResource, HasMetadata primary, Context context) {
      return Result.nonComputed(true);
    }
  };

  R update(R actual, R desired, P primary, Context context);

  Result<R> match(R actualResource, P primary, Context context);
}
