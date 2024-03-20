package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.List;

public interface IndexedResourceCache<T extends HasMetadata> extends ResourceCache<T> {
  List<T> byIndex(String indexName, String indexKey);
}
