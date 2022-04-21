package io.javaoperatorsdk.operator.processing.event.source;

import java.util.function.Function;

public interface IDMapper<R> extends Function<R, String> {

  /**
   * If a polling event source handles only single secondary resources
   *
   * @return static id mapper, all resources are mapped for same id
   * @param <T>
   */
  static <T> IDMapper<T> singleResourceIDMapper() {
    return r -> "id";
  }

}
