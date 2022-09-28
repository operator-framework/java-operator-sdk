package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface ResourceDiscriminator<R, P extends HasMetadata> {

  Optional<R> distinguish(Class<R> resource, P primary, Context<P> context);

}
