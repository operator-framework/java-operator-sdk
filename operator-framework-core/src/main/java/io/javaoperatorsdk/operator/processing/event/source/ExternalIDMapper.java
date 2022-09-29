package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface ExternalIDMapper<P extends HasMetadata, R, T> {

  Set<T> getExternalIDs(P resource);


}
