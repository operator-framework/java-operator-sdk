package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Primary to Secondary mapper only needed in some cases, typically when there is many-to-one or
 * many-to-many relation between primary and secondary resources. If there is owner reference (or
 * reference with annotations) from secondary to primary this is not needed. See
 * PrimaryToSecondaryIT integration tests that handles many-to-many relationship.
 *
 * @param <P> primary resource type
 */
public interface PrimaryToSecondaryMapper<P extends HasMetadata> {

  Set<ResourceID> toSecondaryResourceIDs(P primary);
}
