package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.zjsonpatch.JsonDiff;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Matcher;

public class GenericKubernetesResourceMatcher<R extends HasMetadata> implements Matcher<R> {

  private GenericKubernetesResourceMatcher() {}

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <R extends HasMetadata> Matcher<R> matcherFor(Class<R> resourceType) {
    if (Secret.class.isAssignableFrom(resourceType)) {
      return (actual, desired, context) -> ResourceComparators.compareSecretData((Secret) desired,
          (Secret) actual);
    } else if (ConfigMap.class.isAssignableFrom(resourceType)) {
      return (actual, desired, context) -> ResourceComparators
          .compareConfigMapData((ConfigMap) desired, (ConfigMap) actual);
    } else {
      return new GenericKubernetesResourceMatcher();
    }
  }

  @Override
  public boolean match(R actualResource, R desiredResource, Context context) {
    final var objectMapper = context.getConfigurationService().getObjectMapper();
    // reflection will be replaced by this:
    // https://github.com/fabric8io/kubernetes-client/issues/3816
    var desiredSpecNode = objectMapper.valueToTree(ReconcilerUtils.getSpec(desiredResource));
    var actualSpecNode = objectMapper.valueToTree(ReconcilerUtils.getSpec(actualResource));
    var diffJsonPatch = JsonDiff.asJson(desiredSpecNode, actualSpecNode);
    for (int i = 0; i < diffJsonPatch.size(); i++) {
      String operation = diffJsonPatch.get(i).get("op").asText();
      if (!operation.equals("add")) {
        return false;
      }
    }
    return true;
  }
}
