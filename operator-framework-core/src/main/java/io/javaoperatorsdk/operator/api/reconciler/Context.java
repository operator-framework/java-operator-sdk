package io.javaoperatorsdk.operator.api.reconciler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedDependentResourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface Context<P extends HasMetadata> {

  Optional<RetryInfo> getRetryInfo();

  default <T> Optional<T> getSecondaryResource(Class<T> expectedType) {
    return getSecondaryResource(expectedType, null);
  }

  <T> Set<T> getSecondaryResources(Class<T> expectedType);

  <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName);

  ControllerConfiguration<P> getControllerConfiguration();

  ManagedDependentResourceContext managedDependentResourceContext();

  ResourceID currentlyReconciledResourceID();

  Map<String, Object> metadata();

  static Map<String, Object> metadataFor(HasMetadata resource) {
    final var metadata = new HashMap<String, Object>();
    fillMetadataFor(resource, metadata);
    return metadata;
  }

  static void fillMetadataFor(HasMetadata resource, Map<String, Object> metadata) {
    final Class<? extends HasMetadata> resourceClass = resource.getClass();
    metadata.put(Constants.RESOURCE_GROUP_KEY, HasMetadata.getGroup(resourceClass));
    metadata.put(Constants.RESOURCE_KIND_KEY, resource.getKind());
    metadata.put(Constants.RESOURCE_VERSION_KEY, HasMetadata.getVersion(resourceClass));
  }
}
