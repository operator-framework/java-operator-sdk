package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.KubernetesClientAware;

public interface ExplicitIDHandler<R, P extends HasMetadata, S extends HasMetadata>
    extends Creator<R, P>, Deleter<P>, KubernetesClientAware {

  default Optional<String> eventSourceName() {
    return Optional.empty();
  }

  Class<S> stateResourceClass();

  S stateResource(P primary, R resource);

  // TODO for the two phase resource create
  // void postIDStored(P primary, R resource, S stateResource);

}
