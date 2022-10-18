package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.KubernetesClientAware;

public interface ExplicitStateHandler<R, P extends HasMetadata, S extends HasMetadata>
    extends Creator<R, P>, Deleter<P>, KubernetesClientAware {

  /**
   * Only needs to be implemented if multiple event sources are present for the target resource
   * class.
   *
   * @return name of the event source to access the state resources.
   */
  default Optional<String> eventSourceName() {
    return Optional.empty();
  }

  Class<S> stateResourceClass();

  S stateResource(P primary, R resource);

}
