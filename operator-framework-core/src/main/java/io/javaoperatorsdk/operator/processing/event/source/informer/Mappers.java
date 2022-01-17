package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;

public class Mappers {

  public static <T extends HasMetadata> PrimaryResourcesRetriever<T> fromAnnotation(
      String nameKey) {
    return fromMetadata(nameKey, null, false);
  }

  public static <T extends HasMetadata> PrimaryResourcesRetriever<T> fromAnnotation(
      String nameKey, String namespaceKey) {
    return fromMetadata(nameKey, namespaceKey, false);
  }

  public static <T extends HasMetadata> PrimaryResourcesRetriever<T> fromLabel(
      String nameKey) {
    return fromMetadata(nameKey, null, true);
  }

  public static <T extends HasMetadata> PrimaryResourcesRetriever<T> fromLabel(
      String nameKey, String namespaceKey) {
    return fromMetadata(nameKey, namespaceKey, true);
  }

  public static <T extends HasMetadata> PrimaryResourcesRetriever<T> fromOwnerReference() {
    return resource -> {
      var ownerReferences = resource.getMetadata().getOwnerReferences();
      if (!ownerReferences.isEmpty()) {
        return Set.of(new ResourceID(ownerReferences.get(0).getName(),
            resource.getMetadata().getNamespace()));
      } else {
        return Collections.emptySet();
      }
    };
  }

  private static <T extends HasMetadata> PrimaryResourcesRetriever<T> fromMetadata(
      String nameKey, String namespaceKey, boolean isLabel) {
    return resource -> {
      final var metadata = resource.getMetadata();
      if (metadata == null) {
        return Collections.emptySet();
      } else {
        final var map = isLabel ? metadata.getLabels() : metadata.getAnnotations();
        if (map == null) {
          return Collections.emptySet();
        }
        var name = map.get(nameKey);
        if (name == null) {
          return Collections.emptySet();
        }
        var namespace =
            namespaceKey == null ? resource.getMetadata().getNamespace() : map.get(namespaceKey);
        return Set.of(new ResourceID(name, namespace));
      }
    };
  }

  public static ResourceID fromString(String cacheKey) {
    if (cacheKey == null) {
      return null;
    }

    final String[] split = cacheKey.split("/");
    switch (split.length) {
      case 1:
        return new ResourceID(split[0]);
      case 2:
        return new ResourceID(split[1], split[0]);
      default:
        throw new IllegalArgumentException("Cannot extract a ResourceID from " + cacheKey);
    }
  }
}
