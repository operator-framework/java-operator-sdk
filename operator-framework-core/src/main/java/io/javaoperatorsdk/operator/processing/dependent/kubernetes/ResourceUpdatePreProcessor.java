package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.zjsonpatch.JsonDiff;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface ResourceUpdatePreProcessor<R extends HasMetadata> {

  R replaceSpecOnActual(R actual, R desired, Context<?> context);

  default boolean matches(R actual, R desired, boolean equality) {
    final var objectMapper = ConfigurationServiceProvider.instance().getObjectMapper();

    // reflection will be replaced by this:
    // https://github.com/fabric8io/kubernetes-client/issues/3816
    var desiredSpecNode = objectMapper.valueToTree(ReconcilerUtils.getSpec(desired));
    var actualSpecNode = objectMapper.valueToTree(ReconcilerUtils.getSpec(actual));
    var diffJsonPatch = JsonDiff.asJson(desiredSpecNode, actualSpecNode);
    // In case of equality is set to true, no diffs are allowed, so we return early if diffs exist
    // On contrary (if equality is false), "add" is allowed for cases when for some
    // resources Kubernetes fills-in values into spec.
    final var diffNumber = diffJsonPatch.size();
    if (equality && diffNumber > 0) {
      return false;
    }
    for (int i = 0; i < diffNumber; i++) {
      String operation = diffJsonPatch.get(i).get("op").asText();
      if (!operation.equals("add")) {
        return false;
      }
    }
    return true;
  }
}
