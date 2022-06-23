package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface PrimaryToSecondaryMapper<P extends HasMetadata> {

  Set<ResourceID> toSecondaryResourceIDs(P primary);
}
