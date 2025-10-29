/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

public class Mappers {

  public static final String DEFAULT_ANNOTATION_FOR_NAME = "io.javaoperatorsdk/primary-name";
  public static final String DEFAULT_ANNOTATION_FOR_NAMESPACE =
      "io.javaoperatorsdk/primary-namespace";
  public static final String DEFAULT_ANNOTATION_FOR_PRIMARY_TYPE =
      "io.javaoperatorsdk/primary-type";

  private Mappers() {}

  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromAnnotation(
      String nameKey, String typeKey, Class<? extends HasMetadata> primaryResourceType) {
    return fromAnnotation(nameKey, null, typeKey, primaryResourceType);
  }

  @SuppressWarnings("unused")
  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromAnnotation(
      String nameKey,
      String namespaceKey,
      String typeKey,
      Class<? extends HasMetadata> primaryResourceType) {
    return fromMetadata(nameKey, namespaceKey, typeKey, primaryResourceType, false);
  }

  @SuppressWarnings("unused")
  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromLabel(
      String nameKey, String typeKey, Class<? extends HasMetadata> primaryResourceType) {
    return fromLabel(nameKey, null, typeKey, primaryResourceType);
  }

  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromDefaultAnnotations(
      Class<? extends HasMetadata> primaryResourceType) {
    return fromAnnotation(
        DEFAULT_ANNOTATION_FOR_NAME,
        DEFAULT_ANNOTATION_FOR_NAMESPACE,
        DEFAULT_ANNOTATION_FOR_PRIMARY_TYPE,
        primaryResourceType);
  }

  @SuppressWarnings("unused")
  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromLabel(
      String nameKey,
      String namespaceKey,
      String typeKey,
      Class<? extends HasMetadata> primaryResourceType) {
    return fromMetadata(nameKey, namespaceKey, typeKey, primaryResourceType, true);
  }

  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromOwnerReferences(
      Class<? extends HasMetadata> primaryResourceType) {
    return fromOwnerReferences(primaryResourceType, false);
  }

  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromOwnerReferences(
      Class<? extends HasMetadata> primaryResourceType, boolean clusterScoped) {
    return fromOwnerReferences(
        HasMetadata.getApiVersion(primaryResourceType),
        HasMetadata.getKind(primaryResourceType),
        clusterScoped);
  }

  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromOwnerReferences(
      HasMetadata primaryResource) {
    return fromOwnerReferences(primaryResource, false);
  }

  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromOwnerReferences(
      HasMetadata primaryResource, boolean clusterScoped) {
    return fromOwnerReferences(
        primaryResource.getApiVersion(), primaryResource.getKind(), clusterScoped);
  }

  public static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromOwnerReferences(
      String apiVersion, String kind, boolean clusterScope) {
    String correctApiVersion = apiVersion.startsWith("/") ? apiVersion.substring(1) : apiVersion;
    return resource ->
        resource.getMetadata().getOwnerReferences().stream()
            .filter(r -> r.getKind().equals(kind) && r.getApiVersion().equals(correctApiVersion))
            .map(or -> ResourceID.fromOwnerReference(resource, or, clusterScope))
            .collect(Collectors.toSet());
  }

  private static <T extends HasMetadata> SecondaryToPrimaryMapper<T> fromMetadata(
      String nameKey,
      String namespaceKey,
      String typeKey,
      Class<? extends HasMetadata> primaryResourceType,
      boolean isLabel) {
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

        String gvkSimple = map.get(typeKey);

        if (gvkSimple != null
            && !GroupVersionKind.fromString(gvkSimple)
                .equals(GroupVersionKind.gvkFor(primaryResourceType))) {
          return Set.of();
        }

        return Set.of(new ResourceID(name, namespace));
      }
    };
  }

  public static ResourceID fromString(String cacheKey) {
    if (cacheKey == null) {
      return null;
    }

    final String[] split = cacheKey.split("/");
    return switch (split.length) {
      case 1 -> new ResourceID(split[0]);
      case 2 -> new ResourceID(split[1], split[0]);
      default -> throw new IllegalArgumentException("Cannot extract a ResourceID from " + cacheKey);
    };
  }

  /**
   * Produces a mapper that will associate a secondary resource with all owners of the primary type.
   */
  public static <OWNER extends HasMetadata, T extends HasMetadata>
      SecondaryToPrimaryMapper<T> fromOwnerType(Class<OWNER> clazz) {
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

  public static class SecondaryToPrimaryFromDefaultAnnotation
      implements SecondaryToPrimaryMapper<HasMetadata> {

    private final Class<? extends HasMetadata> primaryResourceType;

    public SecondaryToPrimaryFromDefaultAnnotation(
        Class<? extends HasMetadata> primaryResourceType) {
      this.primaryResourceType = primaryResourceType;
    }

    @Override
    public Set<ResourceID> toPrimaryResourceIDs(HasMetadata resource) {
      return Mappers.fromDefaultAnnotations(primaryResourceType).toPrimaryResourceIDs(resource);
    }
  }
}
