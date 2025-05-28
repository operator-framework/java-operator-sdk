package io.javaoperatorsdk.operator.processing.event.source;

public interface CacheKeyMapper<R> {

  String keyFor(R resource);

  /**
   * Used if a polling event source handles only single secondary resource. See also docs for:
   * {@link ExternalResourceCachingEventSource}
   *
   * @return static id mapper, all resources are mapped for same id.
   * @param <T> secondary resource type
   */
  static <T> CacheKeyMapper<T> singleResourceCacheKeyMapper() {
    return r -> "id";
  }
}
