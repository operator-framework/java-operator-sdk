package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

/**
 * Helper for the Bulk Dependent Resources to make it more explicit that bulk needs to only
 * implement the index aware match method.
 *
 * @param <R> secondary resource type
 * @param <P> primary resource type
 */
public interface BulkUpdater<R, P extends HasMetadata> extends Updater<R, P> {

  default Matcher.Result<R> match(R actualResource, P primary, Context<P> context) {
    throw new IllegalStateException();
  }

  Matcher.Result<R> match(R actualResource, P primary, int index, Context<P> context);
}
