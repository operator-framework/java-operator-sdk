package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;

// todo is discriminator a good name? it not just discriminates but also reads from cache
public interface ResourceDiscriminator<R, P extends HasMetadata> {

  Optional<R> distinguish(Class<R> resource, P primary, Context<P> context,
      EventSourceRetriever<P> eventSourceRetriever);

}
