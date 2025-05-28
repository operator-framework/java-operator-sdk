package io.javaoperatorsdk.operator.processing.event.source.cache;

public interface ResourceFetcher<K, R> {

  R fetchResource(K key);
}
