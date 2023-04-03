package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Primary to Secondary mapper only needed in some cases, typically when there is many-to-one or
 * many-to-many relation between primary and secondary resources. If there is owner reference (or
 * reference with annotations) from secondary to primary this is not needed. See <a href=
 * "https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/sample/primarytosecondary/JobReconciler.java#L45-L45">Reconciler
 * in PrimaryToSecondaryIT</a> integration tests that handles many-to-many relationship. In other
 * words it is needed when the secondary is not referencing the primary, but other way around. The
 * issue is with a use case when a primary resource references a secondary resource, and the primary
 * created after the secondary, in the case the "context.getSecondaryResource()" would not work
 * without this mapper as intended.
 *
 * @param <P> primary resource type
 */
public interface PrimaryToSecondaryMapper<P extends HasMetadata> {

  Set<ResourceID> toSecondaryResourceIDs(P primary);
}
