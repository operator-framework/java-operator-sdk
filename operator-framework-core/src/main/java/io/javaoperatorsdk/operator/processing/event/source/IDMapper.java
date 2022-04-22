package io.javaoperatorsdk.operator.processing.event.source;

import java.util.function.Function;

public interface IDMapper<R> extends Function<R, String> {

  /**
   * Used if a polling event source handles only single secondary resource. See also docs for:
   * {@link ExternalResourceCachingEventSource}
   *
   * @return static id mapper, all resources are mapped for same id.
   * @param <T> secondary resource type
   */
  static <T> IDMapper<T> singleResourceIDMapper() {
    return r -> "id";
  }

}
