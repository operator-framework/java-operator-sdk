package io.javaoperatorsdk.operator.processing;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface ResourceOwner<R, P extends HasMetadata> {

  /**
   * Retrieves the resource type associated with this ResourceOwner
   *
   * @return the resource type associated with this ResourceOwner
   */
  Class<R> resourceType();

  /**
   * Retrieves the resource associated with the specified primary one, returning the actual state of
   * the resource. Typically, this state might come from a local cache, updated after
   * reconciliation.
   *
   * @param primary the primary resource for which we want to retrieve the secondary resource
   * @return an {@link Optional} containing the secondary resource or {@link Optional#empty()} if it
   *         doesn't exist
   */
  Optional<R> getSecondaryResource(P primary);
}
