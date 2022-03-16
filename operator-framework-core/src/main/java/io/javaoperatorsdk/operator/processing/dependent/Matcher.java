package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface Matcher<R, P extends HasMetadata> {
  interface Result<R> {
    boolean matched();

    default Optional<R> computedDesired() {
      return Optional.empty();
    }

    static <T> Result<T> nonComputed(boolean matched) {
      return () -> matched;
    }

    static <T> Result<T> computed(boolean matched, T computedDesired) {
      return new Result<>() {
        @Override
        public boolean matched() {
          return matched;
        }

        @Override
        public Optional<T> computedDesired() {
          return Optional.of(computedDesired);
        }
      };
    }
  }

  Result<R> match(R actualResource, P primary, Context<P> context);
}
