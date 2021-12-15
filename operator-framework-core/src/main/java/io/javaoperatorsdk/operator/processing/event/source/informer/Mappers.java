package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;

public class Mappers {

  public static <P extends HasMetadata> AssociatedSecondaryIdentifier<P> sameNameAndNamespace() {
    return (primary, registry) -> ResourceID.fromResource(primary);
  }

  public static <T extends HasMetadata, P extends HasMetadata> PrimaryResourcesRetriever<T, P> fromAnnotation(
      String nameKey) {
    return fromMetadata(nameKey, null, false);
  }

  public static <T extends HasMetadata, P extends HasMetadata> PrimaryResourcesRetriever<T, P> fromAnnotation(
      String nameKey, String namespaceKey) {
    return fromMetadata(nameKey, namespaceKey, false);
  }

  public static <T extends HasMetadata, P extends HasMetadata> PrimaryResourcesRetriever<T, P> fromLabel(
      String nameKey) {
    return fromMetadata(nameKey, null, true);
  }

  public static <T extends HasMetadata, P extends HasMetadata> PrimaryResourcesRetriever<T, P> fromLabel(
      String nameKey, String namespaceKey) {
    return fromMetadata(nameKey, namespaceKey, true);
  }

  private static <T extends HasMetadata, P extends HasMetadata> PrimaryResourcesRetriever<T, P> fromMetadata(
      String nameKey, String namespaceKey, boolean isLabel) {
    return (resource, registry) -> {
      final var metadata = resource.getMetadata();
      if (metadata == null) {
        return Collections.emptySet();
      } else {
        final var map = isLabel ? metadata.getLabels() : metadata.getAnnotations();
        var namespace =
            namespaceKey == null ? resource.getMetadata().getNamespace() : map.get(namespaceKey);
        return map != null ? Set.of(new ResourceID(map.get(nameKey), namespace))
            : Collections.emptySet();
      }
    };
  }

  public static <T extends HasMetadata, P extends HasMetadata> PrimaryResourcesRetriever<T, P> fromOwnerReference() {
    return (resource, registry) -> {
      var ownerReferences = resource.getMetadata().getOwnerReferences();
      if (!ownerReferences.isEmpty()) {
        return Set.of(new ResourceID(ownerReferences.get(0).getName(),
            resource.getMetadata().getNamespace()));
      } else {
        return Collections.emptySet();
      }
    };
  }
}
