package io.javaoperatorsdk.operator.processing.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.KubernetesClientAware;

/**
 * Handles external resources where in order to address the resource additional information or
 * persistent state (usually the ID of the resource) is needed to access the current state. These
 * are non Kubernetes resources which when created their ID is generated, so cannot be determined
 * based only on primary resources. In order to manage such dependent resource use this interface
 * for a resource that extends {@link AbstractExternalDependentResource}.
 */
public interface DependentResourceWithExplicitState<R, P extends HasMetadata, S extends HasMetadata>
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

  /**
   * Class of the state resource.
   */
  Class<S> stateResourceClass();

  /** State resource which contains the target state. Usually an ID to address the resource */
  S stateResource(P primary, R resource);

}
