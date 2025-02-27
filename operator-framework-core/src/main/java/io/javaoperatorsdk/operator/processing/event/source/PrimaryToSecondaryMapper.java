package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Identifies the set of secondary resources associated with a given primary resource. This is
 * typically needed when multiple secondary resources can be associated with one or several multiple
 * primary resources *without* a standard way (e.g. owner reference or annotations) to materialize
 * that relations. When owner references are present, a {@code PrimaryToSecondaryMapper} instance
 * should not be needed. In other words, associating such a mapper with your {@link
 * InformerEventSourceConfiguration} is usually needed when your secondary resources are referenced
 * in some way by your primary resource but that this link does not exist in the secondary resource
 * information. The mapper implementation instructs the SDK on how to find all the secondary
 * resources associated with a given primary resource so that this primary resource can properly be
 * reconciled when changes impact the associated secondary resources, even though these don't
 * contain any information allowing to make such an inference.
 *
 * <p>This helps particularly in cases where several secondary resources, listed in some way in the
 * primary resource, need to or can be created before the primary resource exists. In that
 * situation, attempting to retrieve the associated secondary resources by calling {@link
 * io.javaoperatorsdk.operator.api.reconciler.Context#getSecondaryResource(Class)} would fail
 * without providing a mapper to tell JOSDK how to retrieve the secondary resources.
 *
 * <p>You can see an example of this in action in the <a href=
 * "https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/sample/primarytosecondary/JobReconciler.java">Reconciler
 * for the PrimaryToSecondaryIT</a> integration tests that handles many-to-many relationship.
 *
 * @param <P> primary resource type
 */
public interface PrimaryToSecondaryMapper<P extends HasMetadata> {

  Set<ResourceID> toSecondaryResourceIDs(P primary);
}
