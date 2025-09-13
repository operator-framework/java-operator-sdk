package io.javaoperatorsdk.operator.processing.expectation;

import java.util.function.BiPredicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface Expectation<P extends HasMetadata> {

  String UNNAMED = "unnamed";

  boolean isFulfilled(P primary, Context<P> context);

  default String name() {
    return UNNAMED;
  }

  static <P extends HasMetadata> Expectation<P> createExpectation(
      String name, BiPredicate<P, Context<P>> predicate) {
    return new Expectation<>() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public boolean isFulfilled(P primary, Context<P> context) {
        return predicate.test(primary, context);
      }
    };
  }
}
