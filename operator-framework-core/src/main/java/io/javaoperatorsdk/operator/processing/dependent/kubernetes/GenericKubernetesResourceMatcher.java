package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.*;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.zjsonpatch.JsonDiff;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;

import com.fasterxml.jackson.databind.JsonNode;

public class GenericKubernetesResourceMatcher<R extends HasMetadata, P extends HasMetadata>
    implements Matcher<R, P> {

  private final KubernetesDependentResource<R, P> dependentResource;

  private GenericKubernetesResourceMatcher(KubernetesDependentResource<R, P> dependentResource) {
    this.dependentResource = dependentResource;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static <R extends HasMetadata, P extends HasMetadata> Matcher<R, P> matcherFor(
      Class<R> resourceType, KubernetesDependentResource<R, P> dependentResource) {
    return new GenericKubernetesResourceMatcher(dependentResource);
  }

  @Override
  public Result<R> match(R actualResource, P primary, Context<P> context) {
    var desired = dependentResource.desired(primary, context);
    return match(desired, actualResource, true, false,
        false, Collections.emptyList());
  }

  public static <R extends HasMetadata> Result<R> match(R desired, R actualResource,
      boolean considerMetadata) {
    return match(desired, actualResource, considerMetadata, false);
  }

  /**
   * Determines whether the specified actual resource matches the specified desired resource,
   * possibly considering metadata and deeper equality checks.
   *
   * @param desired the desired resource
   * @param actualResource the actual resource
   * @param considerMetadata {@code true} if labels and annotations will be checked for equality,
   *        {@code false} otherwise (meaning that metadata changes will be ignored for matching
   *        purposes)
   * @param equality if {@code false}, the algorithm checks if the properties in the desired
   *        resource spec are same as in the actual resource spec. The reason is that admission
   *        controllers and default Kubernetes controllers might add default values to some
   *        properties which are not set in the desired resources' spec and comparing it with simple
   *        equality check would mean that such resource will not match (while conceptually should).
   *        However, there is an issue with this for example if desired spec contains a list of
   *        values and a value is removed, this still will match the actual state from previous
   *        reconciliation. Setting this parameter to {@code true}, will match the resources only if
   *        all properties and values are equal. This could be implemented also by overriding equals
   *        method of spec, should be done as an optimization - this implementation does not require
   *        that.
   *
   * @return results of matching
   * @param <R> resource
   */
  public static <R extends HasMetadata> Result<R> match(R desired, R actualResource,
      boolean considerMetadata, boolean equality) {
    return match(desired, actualResource, considerMetadata, false, equality,
        Collections.emptyList());
  }

  public static <R extends HasMetadata> Result<R> match(R desired, R actualResource,
      boolean considerMetadata, String... ignoreList) {
    return match(desired, actualResource, considerMetadata, false, false,
        Arrays.asList(ignoreList));
  }

  public static <R extends HasMetadata> Result<R> match(R desired, R actualResource,
      boolean considerMetadata, boolean metadataEquality, String... ignoreList) {
    return match(desired, actualResource, considerMetadata, metadataEquality, false,
        Arrays.asList(ignoreList));
  }

  private static <R extends HasMetadata> Result<R> match(R desired, R actualResource,
      boolean considerMetadata, boolean metadataEquality, boolean specEquality,
      List<String> ignoreList) {

    if (specEquality && !ignoreList.isEmpty()) {
      throw new IllegalArgumentException(
          "Equality should be false in case of ignore list provided");
    }

    final var objectMapper = ConfigurationServiceProvider.instance().getObjectMapper();

    var desiredNode = objectMapper.valueToTree(desired);
    var actualNode = objectMapper.valueToTree(actualResource);
    var wholeDiffJsonPatch = JsonDiff.asJson(desiredNode, actualNode);

    var considerIgnoreList = !specEquality && !ignoreList.isEmpty();

    if (considerMetadata) {
      Optional<Result<R>> res =
          matchMetadata(desired, actualResource, metadataEquality, wholeDiffJsonPatch);
      if (res.isPresent()) {
        return res.orElseThrow();
      }
    }
    if (desired instanceof ConfigMap) {
      return Result.computed(
          ResourceComparators.compareConfigMapData((ConfigMap) desired, (ConfigMap) actualResource),
          desired);
    } else if (desired instanceof Secret) {
      return Result.computed(
          ResourceComparators.compareSecretData((Secret) desired, (Secret) actualResource),
          desired);
    } else {
      return matchSpec(desired, specEquality, ignoreList, wholeDiffJsonPatch, considerIgnoreList);
    }
  }

  private static <R extends HasMetadata> Result<R> matchSpec(R desired, boolean specEquality,
      List<String> ignoreList, JsonNode wholeDiffJsonPatch, boolean considerIgnoreList) {
    // reflection will be replaced by this:
    // https://github.com/fabric8io/kubernetes-client/issues/3816
    var specDiffJsonPatch = getDiffsWithPathSuffix(wholeDiffJsonPatch, "/spec");
    // In case of equality is set to true, no diffs are allowed, so we return early if diffs exist
    // On contrary (if equality is false), "add" is allowed for cases when for some
    // resources Kubernetes fills-in values into spec.
    if (specEquality && !specDiffJsonPatch.isEmpty()) {
      return Result.computed(false, desired);
    }
    if (considerIgnoreList) {
      if (!allDiffsOnIgnoreList(specDiffJsonPatch, ignoreList)) {
        return Result.computed(false, desired);
      }
    } else {
      if (!allDiffsAreAddOps(specDiffJsonPatch)) {
        return Result.computed(false, desired);
      }
    }
    return Result.computed(true, desired);
  }

  private static <R extends HasMetadata> Optional<Result<R>> matchMetadata(R desired,
      R actualResource, boolean metadataEquality, JsonNode wholeDiffJsonPatch) {
    if (metadataEquality) {
      final var desiredMetadata = desired.getMetadata();
      final var actualMetadata = actualResource.getMetadata();

      final var matched =
          Objects.equals(desiredMetadata.getAnnotations(), actualMetadata.getAnnotations()) &&
              Objects.equals(desiredMetadata.getLabels(), actualMetadata.getLabels());
      if (!matched) {
        return Optional.of(Result.computed(false, desired));
      }
    } else {
      var metadataJSonDiffs = getDiffsWithPathSuffix(wholeDiffJsonPatch,
          "/metadata/labels",
          "/metadata/annotations");
      if (!allDiffsAreAddOps(metadataJSonDiffs)) {
        return Optional.of(Result.computed(false, desired));
      }
    }
    return Optional.empty();
  }

  private static boolean allDiffsAreAddOps(List<JsonNode> metadataJSonDiffs) {
    if (metadataJSonDiffs.isEmpty()) {
      return true;
    }
    return metadataJSonDiffs.stream().allMatch(n -> "add".equals(n.get("op").asText()));
  }

  private static boolean allDiffsOnIgnoreList(List<JsonNode> metadataJSonDiffs,
      List<String> ignoreList) {
    if (metadataJSonDiffs.isEmpty()) {
      return false;
    }
    return metadataJSonDiffs.stream().allMatch(n -> {
      var path = n.get("path").asText();
      return ignoreList.stream().anyMatch(path::startsWith);
    });
  }

  private static List<JsonNode> getDiffsWithPathSuffix(JsonNode diffJsonPatch,
      String... ignorePaths) {
    var res = new ArrayList<JsonNode>();
    var prefixList = Arrays.asList(ignorePaths);
    for (int i = 0; i < diffJsonPatch.size(); i++) {
      var node = diffJsonPatch.get(i);
      String path = diffJsonPatch.get(i).get("path").asText();
      if (prefixList.stream().anyMatch(path::startsWith)) {
        res.add(node);
      }
    }
    return res;
  }

  /**
   * Determines whether the specified actual resource matches the desired state defined by the
   * specified {@link KubernetesDependentResource} based on the observed state of the associated
   * specified primary resource.
   *
   * @param dependentResource the {@link KubernetesDependentResource} implementation used to
   *        computed the desired state associated with the specified primary resource
   * @param actualResource the observed dependent resource for which we want to determine whether it
   *        matches the desired state or not
   * @param primary the primary resource from which we want to compute the desired state
   * @param context the {@link Context} instance within which this method is called
   * @param considerMetadata {@code true} to consider the metadata of the actual resource when
   *        determining if it matches the desired state, {@code false} if matching should occur only
   *        considering the spec of the resources
   * @return a {@link io.javaoperatorsdk.operator.processing.dependent.Matcher.Result} object
   * @param <R> the type of resource we want to determine whether they match or not
   * @param <P> the type of primary resources associated with the secondary resources we want to
   *        match
   * @param strongEquality if the resource should match exactly
   */
  public static <R extends HasMetadata, P extends HasMetadata> Result<R> match(
      KubernetesDependentResource<R, P> dependentResource, R actualResource, P primary,
      Context<P> context, boolean considerMetadata, boolean strongEquality) {
    final var desired = dependentResource.desired(primary, context);
    return match(desired, actualResource, considerMetadata, strongEquality);
  }

  /**
   * Determines whether the specified actual resource matches the desired state defined by the
   * specified {@link KubernetesDependentResource} based on the observed state of the associated
   * specified primary resource.
   *
   * @param dependentResource the {@link KubernetesDependentResource} implementation used to compute
   *        the desired state associated with the specified primary resource
   * @param actualResource the observed dependent resource for which we want to determine whether it
   *        matches the desired state or not
   * @param primary the primary resource from which we want to compute the desired state
   * @param context the {@link Context} instance within which this method is called
   * @param considerMetadata {@code true} to consider the metadata of the actual resource when
   *        determining if it matches the desired state, {@code false} if matching should occur only
   *        considering the spec of the resources
   * @return a {@link io.javaoperatorsdk.operator.processing.dependent.Matcher.Result} object
   * @param <R> the type of resource we want to determine whether they match or not
   * @param <P> the type of primary resources associated with the secondary resources we want to
   *        match
   * @param ignorePaths are paths in the resource that are ignored on matching (basically an ignore
   *        list). All changes with a target prefix path on a calculated JSON Patch between actual
   *        and desired will be ignored. If there are other changes, non-present on ignore list
   *        match fails.
   *
   */
  public static <R extends HasMetadata, P extends HasMetadata> Result<R> match(
      KubernetesDependentResource<R, P> dependentResource, R actualResource, P primary,
      Context<P> context, boolean considerMetadata, String... ignorePaths) {
    final var desired = dependentResource.desired(primary, context);
    return match(desired, actualResource, considerMetadata, ignorePaths);
  }

  public static <R extends HasMetadata, P extends HasMetadata> Result<R> match(
      KubernetesDependentResource<R, P> dependentResource, R actualResource, P primary,
      Context<P> context, boolean considerMetadata, boolean metadataEquality,
      String... ignorePaths) {
    final var desired = dependentResource.desired(primary, context);
    return match(desired, actualResource, considerMetadata, metadataEquality, ignorePaths);
  }

  public static <R extends HasMetadata, P extends HasMetadata> Result<R> match(
      KubernetesDependentResource<R, P> dependentResource, R actualResource, P primary,
      Context<P> context, boolean considerMetadata) {
    final var desired = dependentResource.desired(primary, context);
    return match(desired, actualResource, considerMetadata, false);
  }

  public static <R extends HasMetadata, P extends HasMetadata> Result<R> match(
      KubernetesDependentResource<R, P> dependentResource, R actualResource, P primary,
      Context<P> context, boolean considerMetadata, boolean metadataEquality,
      boolean strongEquality) {
    final var desired = dependentResource.desired(primary, context);
    return match(desired, actualResource, considerMetadata, metadataEquality, strongEquality,
        Collections.emptyList());
  }
}
