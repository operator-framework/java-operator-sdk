package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class VoidResourceDiscriminator<R, P extends HasMetadata>
    implements ResourceDiscriminator<R, P> {

  @Override
  public Optional<R> distinguish(Class<R> resource, P primary, Context<P> context) {
    throw new UnsupportedOperationException();
  }
}
