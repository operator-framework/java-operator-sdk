package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface Updater<R, P extends HasMetadata> extends Matcher<R, P> {

  R update(R actual, R desired, P primary, Context<P> context);
}
