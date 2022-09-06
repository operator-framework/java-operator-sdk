package io.javaoperatorsdk.operator.sample.indexdiscriminator;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import static io.javaoperatorsdk.operator.sample.indexdiscriminator.IndexDiscriminatorTestReconciler.configMapKeyFromPrimary;

public class IndexDiscriminator
    implements ResourceDiscriminator<ConfigMap, IndexDiscriminatorTestCustomResource> {

  private final String indexName;
  private final String nameSuffix;

  public IndexDiscriminator(String indexName, String nameSuffix) {
    this.indexName = indexName;
    this.nameSuffix = nameSuffix;
  }

  @Override
  public Optional<ConfigMap> distinguish(Class<ConfigMap> resource,
      IndexDiscriminatorTestCustomResource primary,
      Context<IndexDiscriminatorTestCustomResource> context) {

    InformerEventSource<ConfigMap, IndexDiscriminatorTestCustomResource> eventSource =
        (InformerEventSource<ConfigMap, IndexDiscriminatorTestCustomResource>) context
            .eventSourceRetriever()
            .getResourceEventSourceFor(ConfigMap.class);
    var resources = eventSource.byIndex(indexName, configMapKeyFromPrimary(primary, nameSuffix));
    if (resources.isEmpty()) {
      return Optional.empty();
    } else if (resources.size() > 1) {
      throw new IllegalStateException("more than one resource");
    } else {
      return Optional.of(resources.get(0));
    }
  }
}
