package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

public class Mappers {

  public static <T extends HasMetadata> Function<T, Set<CustomResourceID>> fromAnnotation(
      String nameKey) {
    return fromMetadata(nameKey, null, false);
  }

  public static <T extends HasMetadata> Function<T, Set<CustomResourceID>> fromAnnotation(
      String nameKey, String namespaceKey) {
    return fromMetadata(nameKey, namespaceKey, false);
  }

  public static <T extends HasMetadata> Function<T, Set<CustomResourceID>> fromLabel(
      String nameKey) {
    return fromMetadata(nameKey, null, true);
  }

  public static <T extends HasMetadata> Function<T, Set<CustomResourceID>> fromLabel(
      String nameKey, String namespaceKey) {
    return fromMetadata(nameKey, namespaceKey, true);
  }

  private static <T extends HasMetadata> Function<T, Set<CustomResourceID>> fromMetadata(
      String nameKey, String namespaceKey, boolean isLabel) {
    return resource -> {
      final var metadata = resource.getMetadata();
      if (metadata == null) {
        return Collections.emptySet();
      } else {
        final var map = isLabel ? metadata.getLabels() : metadata.getAnnotations();
        var namespace =
            namespaceKey == null ? resource.getMetadata().getNamespace() : map.get(namespaceKey);
        return map != null ? Set.of(new CustomResourceID(map.get(nameKey), namespace))
            : Collections.emptySet();
      }
    };
  }
}
