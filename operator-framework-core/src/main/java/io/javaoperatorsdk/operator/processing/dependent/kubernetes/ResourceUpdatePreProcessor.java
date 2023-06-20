package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.zjsonpatch.JsonDiff;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import com.fasterxml.jackson.databind.JsonNode;

import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher.allDiffsAreAddOps;
import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher.getDiffsImpactingPathsWithPrefixes;
import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher.nodeIsChildOf;

public interface ResourceUpdatePreProcessor<R extends HasMetadata> {

  String SPEC = "/spec";

  R replaceSpecOnActual(R actual, R desired, Context<?> context);

  default boolean matches(R actual, R desired, boolean equality, Context<?> context,
      String[] ignoredPaths) {

    final var objectMapper = context.getClient().getKubernetesSerialization();
    var desiredNode = objectMapper.convertValue(desired, JsonNode.class);
    var actualNode = objectMapper.convertValue(actual, JsonNode.class);
    var wholeDiffJsonPatch = JsonDiff.asJson(desiredNode, actualNode);

    final List<String> ignoreList =
        ignoredPaths != null && ignoredPaths.length > 0 ? Arrays.asList(ignoredPaths)
            : Collections.emptyList();
    // reflection will be replaced by this:
    // https://github.com/fabric8io/kubernetes-client/issues/3816
    var specDiffJsonPatch = getDiffsImpactingPathsWithPrefixes(wholeDiffJsonPatch, SPEC);
    // In case of equality is set to true, no diffs are allowed, so we return early if diffs exist
    // On contrary (if equality is false), "add" is allowed for cases when for some
    // resources Kubernetes fills-in values into spec.
    if (equality && !specDiffJsonPatch.isEmpty()) {
      return false;
    }
    if (!equality && !ignoreList.isEmpty()) {
      if (!allDiffsOnIgnoreList(specDiffJsonPatch, ignoreList)) {
        return false;
      }
    } else {
      if (!allDiffsAreAddOps(specDiffJsonPatch)) {
        return false;
      }
    }
    return true;
  }

  private static boolean allDiffsOnIgnoreList(List<JsonNode> metadataJSonDiffs,
      List<String> ignoreList) {
    if (metadataJSonDiffs.isEmpty()) {
      return false;
    }
    return metadataJSonDiffs.stream().allMatch(n -> nodeIsChildOf(n, ignoreList));
  }

}
