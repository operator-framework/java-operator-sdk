package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface ResourceUpdatePreProcessor<R extends HasMetadata> {

  R replaceSpecOnActual(R actual, R desired, Context<?> context);
}
