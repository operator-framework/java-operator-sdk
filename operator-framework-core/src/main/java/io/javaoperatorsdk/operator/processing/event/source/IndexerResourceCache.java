package io.javaoperatorsdk.operator.processing.event.source;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface IndexerResourceCache<T extends HasMetadata> extends ResourceCache<T> {

  void addIndexers(Map<String, Function<T, List<String>>> indexers);

  List<T> byIndex(String indexName, String indexKey);

}
