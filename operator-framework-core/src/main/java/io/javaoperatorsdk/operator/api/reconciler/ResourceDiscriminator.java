package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.Optional;

public interface ResourceDiscriminator<R, P extends HasMetadata> {

  Optional<R> distinguish(Class<R> resource, P primary, Context<P> context);

}
