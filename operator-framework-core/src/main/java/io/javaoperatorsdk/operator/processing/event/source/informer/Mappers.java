package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class Mappers {

  public static <T extends HasMetadata> Function<T, Set<ResourceID>> fromAnnotation(
      String nameKey) {
    return fromMetadata(nameKey, null, false);
  }

  public static <T extends HasMetadata> Function<T, Set<ResourceID>> fromAnnotation(
      String nameKey, String namespaceKey) {
    return fromMetadata(nameKey, namespaceKey, false);
  }

  public static <T extends HasMetadata> Function<T, Set<ResourceID>> fromLabel(
      String nameKey) {
    return fromMetadata(nameKey, null, true);
  }

  public static <T extends HasMetadata> Function<T, Set<ResourceID>> fromLabel(
      String nameKey, String namespaceKey) {
    return fromMetadata(nameKey, namespaceKey, true);
  }

  private static <T extends HasMetadata> Function<T, Set<ResourceID>> fromMetadata(
      String nameKey, String namespaceKey, boolean isLabel) {
    return resource -> {
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
}
