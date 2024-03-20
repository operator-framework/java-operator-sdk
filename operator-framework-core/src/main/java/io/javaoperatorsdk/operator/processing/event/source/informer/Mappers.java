package io.javaoperatorsdk.operator.processing.event.source.informer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class Mappers {

  public static final String DEFAULT_ANNOTATION_FOR_NAME = "io.javaoperatorsdk/primary-name";
  public static final String DEFAULT_ANNOTATION_FOR_NAMESPACE =
      "io.javaoperatorsdk/primary-namespace";

  private Mappers() {}

  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromAnnotation(
      String nameKey) {
    return fromMetadata(nameKey, null, false);
  }

  @SuppressWarnings("unused")
  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromAnnotation(
      String nameKey, String namespaceKey) {
    return fromMetadata(nameKey, namespaceKey, false);
  }

  @SuppressWarnings("unused")
  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromLabel(String nameKey) {
    return fromMetadata(nameKey, null, true);
  }

  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromDefaultAnnotations() {
    return fromMetadata(DEFAULT_ANNOTATION_FOR_NAME, DEFAULT_ANNOTATION_FOR_NAMESPACE, false);
  }

  @SuppressWarnings("unused")
  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromLabel(
      String nameKey, String namespaceKey) {
    return fromMetadata(nameKey, namespaceKey, true);
  }

  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromOwnerReference() {
    return fromOwnerReference(false);
  }

  /**
   * @param clusterScope if the owner is a cluster scoped resource
   * @return mapper
   * @param <T> type of the secondary resource, where the owner reference is
   */
  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromOwnerReference(
      boolean clusterScope) {
    return resource -> ResourceID.fromFirstOwnerReference(resource, clusterScope).map(Set::of)
        .orElseGet(Collections::emptySet);
  }

  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromOwnerReferences(
      boolean clusterScope) {
    return resource -> resource.getMetadata().getOwnerReferences().stream()
        .map(or -> ResourceID.fromOwnerReference(resource, or, clusterScope))
        .collect(Collectors.toSet());
  }

  private static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromMetadata(
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

  /**
   * Produces a mapper that will associate a secondary resource with all owners of the primary type.
   */
  public static <OWNER extends HasMetadata, T extends HasMetadata> SecondaryToPrimaryMapper<T> fromOwnerType(
      Class<OWNER> clazz) {
    String kind = HasMetadata.getKind(clazz);
    return resource -> {
      var meta = resource.getMetadata();
      if (meta == null) {
        return Set.of();
      }
      var owners = meta.getOwnerReferences();
      if (owners == null || owners.isEmpty()) {
        return Set.of();
      }
      return owners.stream()
          .filter(it -> kind.equals(it.getKind()))
          .map(it -> new ResourceID(it.getName(), resource.getMetadata().getNamespace()))
          .collect(Collectors.toSet());
    };
  }
}
