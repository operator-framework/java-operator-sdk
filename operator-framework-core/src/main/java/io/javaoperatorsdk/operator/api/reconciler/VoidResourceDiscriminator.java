package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;

public class VoidResourceDiscriminator<R, P extends HasMetadata>
    implements ResourceDiscriminator<R, P> {

  @Override
  public Optional<R> distinguish(Class<R> resource, P primary, Context<P> context,
      EventSourceRetriever<P> eventSourceRetriever) {
    throw new UnsupportedOperationException();
  }
}
