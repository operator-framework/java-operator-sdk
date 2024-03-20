package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.IndexedResourceCache;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface IndexerResourceCache<T extends HasMetadata> extends IndexedResourceCache<T> {

  void addIndexers(Map<String, Function<T, List<String>>> indexers);

  default void addIndexer(String name, Function<T, List<String>> indexer) {
    addIndexers(Map.of(name, indexer));
  }
}
