package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class Mappers {
  public static <T extends HasMetadata> InformerEventSource.ResourceToRelatedCustomResourceUIDMapper<T> fromAnnotation(
      String annotationKey) {
    return fromMetadata(annotationKey, false);
  }

  public static <T extends HasMetadata> InformerEventSource.ResourceToRelatedCustomResourceUIDMapper<T> fromLabel(
      String labelKey) {
    return fromMetadata(labelKey, true);
  }

  private static <T extends HasMetadata> InformerEventSource.ResourceToRelatedCustomResourceUIDMapper<T> fromMetadata(
      String key, boolean isLabel) {
    return resource -> {
      final var metadata = resource.getMetadata();
      if (metadata == null) {
        return Collections.emptySet();
      } else {
        final var map = isLabel ? metadata.getLabels() : metadata.getAnnotations();
        return map != null ? Set.of(map.get(key)) : Collections.emptySet();
      }
    };
  }
}
