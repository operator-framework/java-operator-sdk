package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface PrimaryToSecondaryMapper<P extends HasMetadata> {

  Set<String> toSecondaryResourceIdentifiers(P primary);

}
