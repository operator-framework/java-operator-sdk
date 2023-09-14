package io.javaoperatorsdk.operator.api.reconciler;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface IndexedResourceCache<T extends HasMetadata> extends ResourceCache<T> {
  List<T> byIndex(String indexName, String indexKey);
}
