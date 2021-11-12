package io.javaoperatorsdk.operator.processing;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

public interface ResourceCache<T extends HasMetadata> {

  Optional<T> getCustomResource(CustomResourceID resourceID);

}
