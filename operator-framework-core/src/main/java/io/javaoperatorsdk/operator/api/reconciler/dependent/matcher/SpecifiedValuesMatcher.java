package io.javaoperatorsdk.operator.api.reconciler.dependent.matcher;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;

public class SpecifiedValuesMatcher<R extends HasMetadata, P extends HasMetadata>
        implements ResourceMatcher<R,P>{

    @Override
    public void onEventSourceInit(EventSourceContext<P> context) {
        ResourceMatcher.super.onEventSourceInit(context);
    }

    @Override
    public void onCreated(R desired, R created) {
        ResourceMatcher.super.onCreated(desired, created);
    }

    @Override
    public boolean match(R actual, R desired, Context context) {
        return false;
    }
}
