package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Objects;

import io.javaoperatorsdk.operator.api.reconciler.Context;

@SuppressWarnings("rawtypes")
public interface Matcher<R> {
  Matcher DEFAULT =
      (actualResource, desiredResource, context) -> Objects.equals(actualResource, desiredResource);

  boolean match(R actualResource, R desiredResource, Context context);
}
