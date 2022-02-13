package io.javaoperatorsdk.operator.api.reconciler.dependent.matcher;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;

public interface ResourceMatcher<R extends HasMetadata, P extends HasMetadata> {

    default void onEventSourceInit(EventSourceContext<P> context) {}

    default void onCreated(R desired, R created) {}

    boolean match(R actual, R desired, Context context);

}
