package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;
import java.util.function.Function;

/**
 * Uses a custom index of {@link InformerEventSource} to access the target resource. The index needs
 * to be explicitly created when the event source is defined. This approach improves the performance
 * to access the resource.
 */
public class IndexDiscriminator<R extends HasMetadata, P extends HasMetadata>
    implements ResourceDiscriminator<R, P> {

  private final String indexName;
  private final String eventSourceName;
  private final Function<P, String> keyMapper;

  public IndexDiscriminator(String indexName, Function<P, String> keyMapper) {
    this(indexName, null, keyMapper);
  }

  public IndexDiscriminator(String indexName, String eventSourceName,
      Function<P, String> keyMapper) {
    this.indexName = indexName;
    this.eventSourceName = eventSourceName;
    this.keyMapper = keyMapper;
  }

  @Override
  public Optional<R> distinguish(Class<R> resource,
      P primary,
      Context<P> context) {

    InformerEventSource<R, P> eventSource =
        (InformerEventSource<R, P>) context
            .eventSourceRetriever()
            .getResourceEventSourceFor(resource, eventSourceName);
    var resources = eventSource.byIndex(indexName, keyMapper.apply(primary));
    if (resources.isEmpty()) {
      return Optional.empty();
    } else if (resources.size() > 1) {
      throw new IllegalStateException("More than one resource found");
    } else {
      return Optional.of(resources.get(0));
    }
  }
}
