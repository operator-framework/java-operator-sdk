package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface Matcher<R> {
  boolean match(R actualResource, R desiredResource, Context context);
}
