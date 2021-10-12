package io.javaoperatorsdk.operator.processing;

import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

public interface ResourceCache<T extends CustomResource<?, ?>> {

  Optional<T> getCustomResource(CustomResourceID resourceID);

}
