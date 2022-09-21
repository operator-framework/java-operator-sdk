package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface BulkUpdater<R, P extends HasMetadata> extends Updater<R, P> {

  default Matcher.Result<R> match(R actualResource, P primary, Context<P> context) {
    throw new IllegalStateException();
  }

  Matcher.Result<R> match(R actualResource, P primary, int index, Context<P> context);
}
