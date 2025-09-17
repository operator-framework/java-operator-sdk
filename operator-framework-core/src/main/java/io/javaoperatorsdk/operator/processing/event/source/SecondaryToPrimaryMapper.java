package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Maps secondary resource to primary resources.
 *
 * @param <R>
 */
@FunctionalInterface
public interface SecondaryToPrimaryMapper<R> {
  /**
   * @param resource - secondary
   * @return set of primary resource IDs
   */
  Set<ResourceID> toPrimaryResourceIDs(R resource);
}
