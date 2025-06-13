package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.zjsonpatch.JsonDiff;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;

import com.fasterxml.jackson.databind.JsonNode;

public class GenericKubernetesResourceMatcher<R extends HasMetadata, P extends HasMetadata> {

  private static final String SPEC = "/spec";
  private static final String METADATA = "/metadata";
  private static final String ADD = "add";
  private static final String OP = "op";
  private static final List<String> IGNORED_FIELDS = List.of("/apiVersion", "/kind", "/status");
  public static final String METADATA_LABELS = "/metadata/labels";
  public static final String METADATA_ANNOTATIONS = "/metadata/annotations";

  private static final String PATH = "path";
  private static final String[] EMPTY_ARRAY = {};

  /**
   * Determines whether the specified actual resource matches the specified desired resource,
   * possibly considering metadata and deeper equality checks.
   *
   * @param desired the desired resource
   * @param actualResource the actual resource
   * @param labelsAndAnnotationsEquality if true labels and annotation match exactly in the actual
   *     and desired state if false, additional elements are allowed in actual annotations.
   *     Considered only if considerLabelsAndAnnotations is true.
   * @param valuesEquality if {@code false}, the algorithm checks if the properties in the desired
   *     resource spec (or other non metadata value) are same as in the actual resource spec. The
   *     reason is that admission controllers and default Kubernetes controllers might add default
   *     values to some properties which are not set in the desired resources' spec and comparing it
   *     with simple equality check would mean that such resource will not match (while conceptually
   *     should). However, there is an issue with this for example if desired spec contains a list
   *     of values and a value is removed, this still will match the actual state from previous
   *     reconciliation. Setting this parameter to {@code true}, will match the resources only if
   *     all properties and values are equal. This could be implemented also by overriding equals
   *     method of spec, should be done as an optimization - this implementation does not require
   *     that.
   * @param <R> resource
   * @return results of matching
   */
  public static <R extends HasMetadata, P extends HasMetadata> Matcher.Result<R> match(
      R desired,
      R actualResource,
      boolean labelsAndAnnotationsEquality,
      boolean valuesEquality,
      Context<P> context) {
    return match(
        desired,
        actualResource,
        labelsAndAnnotationsEquality,
        valuesEquality,
        context,
        EMPTY_ARRAY);
  }

  public static <R extends HasMetadata, P extends HasMetadata> Matcher.Result<R> match(
      R desired, R actualResource, Context<P> context) {
    return match(desired, actualResource, false, false, context, EMPTY_ARRAY);
  }

  /**
   * Determines whether the specified actual resource matches the specified desired resource,
   * possibly considering metadata and deeper equality checks.
   *
   * @param desired the desired resource
   * @param actualResource the actual resource
   * @param labelsAndAnnotationsEquality if true labels and annotation match exactly in the actual
   *     and desired state if false, additional elements are allowed in actual annotations.
   *     Considered only if considerLabelsAndAnnotations is true.
   * @param ignorePaths are paths in the resource that are ignored on matching (basically an ignore
   *     list). All changes with a target prefix path on a calculated JSON Patch between actual and
   *     desired will be ignored. If there are other changes, non-present on ignore list match
   *     fails.
   * @param <R> resource
   * @return results of matching
   */
  public static <R extends HasMetadata, P extends HasMetadata> Matcher.Result<R> match(
      R desired,
      R actualResource,
      boolean labelsAndAnnotationsEquality,
      Context<P> context,
      String... ignorePaths) {
    return match(
        desired, actualResource, labelsAndAnnotationsEquality, false, context, ignorePaths);
  }

  /**
   * Determines whether the specified actual resource matches the desired state defined by the
   * specified {@link KubernetesDependentResource} based on the observed state of the associated
   * specified primary resource.
   *
   * @param dependentResource the {@link KubernetesDependentResource} implementation used to compute
   *     the desired state associated with the specified primary resource
   * @param actualResource the observed dependent resource for which we want to determine whether it
   *     matches the desired state or not
   * @param primary the primary resource from which we want to compute the desired state
   * @param context the {@link Context} instance within which this method is called
   * @param labelsAndAnnotationsEquality if true labels and annotation match exactly in the actual
   *     and desired state if false, additional elements are allowed in actual annotations.
   *     Considered only if considerLabelsAndAnnotations is true.
   * @param <R> the type of resource we want to determine whether they match or not
   * @param <P> the type of primary resources associated with the secondary resources we want to
   *     match
   * @param ignorePaths are paths in the resource that are ignored on matching (basically an ignore
   *     list). All changes with a target prefix path on a calculated JSON Patch between actual and
   *     desired will be ignored. If there are other changes, non-present on ignore list match
   *     fails.
   * @return a {@link io.javaoperatorsdk.operator.processing.dependent.Matcher.Result} object
   */
  public static <R extends HasMetadata, P extends HasMetadata> Matcher.Result<R> match(
      KubernetesDependentResource<R, P> dependentResource,
      R actualResource,
      P primary,
      Context<P> context,
      boolean labelsAndAnnotationsEquality,
      String... ignorePaths) {
    final var desired = dependentResource.cachedDesired(primary, context);
    return match(desired, actualResource, labelsAndAnnotationsEquality, context, ignorePaths);
  }

  public static <R extends HasMetadata, P extends HasMetadata> Matcher.Result<R> match(
      KubernetesDependentResource<R, P> dependentResource,
      R actualResource,
      P primary,
      Context<P> context,
      boolean specEquality,
      boolean labelsAndAnnotationsEquality,
      String... ignorePaths) {
    final var desired = dependentResource.cachedDesired(primary, context);
    return match(
        desired, actualResource, labelsAndAnnotationsEquality, specEquality, context, ignorePaths);
  }

  public static <R extends HasMetadata, P extends HasMetadata> Matcher.Result<R> match(
      R desired,
      R actualResource,
      boolean labelsAndAnnotationsEquality,
      boolean valuesEquality,
      Context<P> context,
      String... ignoredPaths) {
    final List<String> ignoreList =
        ignoredPaths != null && ignoredPaths.length > 0
            ? Arrays.asList(ignoredPaths)
            : Collections.emptyList();

    if (valuesEquality && !ignoreList.isEmpty()) {
      throw new IllegalArgumentException(
          "Equality should be false in case of ignore list provided");
    }

    final var kubernetesSerialization = context.getClient().getKubernetesSerialization();
    var desiredNode = kubernetesSerialization.convertValue(desired, JsonNode.class);
    var actualNode = kubernetesSerialization.convertValue(actualResource, JsonNode.class);
    var wholeDiffJsonPatch = JsonDiff.asJson(desiredNode, actualNode);

    boolean matched = true;
    for (int i = 0; i < wholeDiffJsonPatch.size() && matched; i++) {
      var node = wholeDiffJsonPatch.get(i);
      if (nodeIsChildOf(node, List.of(SPEC))) {
        matched = match(valuesEquality, node, ignoreList);
      } else if (nodeIsChildOf(node, List.of(METADATA))) {
        // conditionally consider labels and annotations
        if (nodeIsChildOf(node, List.of(METADATA_LABELS, METADATA_ANNOTATIONS))) {
          matched = match(labelsAndAnnotationsEquality, node, Collections.emptyList());
        }
      } else if (!nodeIsChildOf(node, IGNORED_FIELDS)) {
        matched = match(valuesEquality, node, ignoreList);
      }
    }

    return Matcher.Result.computed(matched, desired);
  }

  private static boolean match(boolean equality, JsonNode diff, final List<String> ignoreList) {
    if (equality) {
      return false;
    }
    if (!ignoreList.isEmpty()) {
      return nodeIsChildOf(diff, ignoreList);
    }
    return ADD.equals(diff.get(OP).asText());
  }

  static boolean nodeIsChildOf(JsonNode n, List<String> prefixes) {
    var path = getPath(n);
    return prefixes.stream().anyMatch(path::startsWith);
  }

  static String getPath(JsonNode n) {
    return n.get(PATH).asText();
  }
}
