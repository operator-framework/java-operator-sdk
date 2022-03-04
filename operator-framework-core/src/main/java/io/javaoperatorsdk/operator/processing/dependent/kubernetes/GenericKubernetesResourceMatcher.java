package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.zjsonpatch.JsonDiff;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Matcher;

public class GenericKubernetesResourceMatcher<R extends HasMetadata, P extends HasMetadata>
    implements Matcher<R, P> {

  private final KubernetesDependentResource<R, P> dependentResource;

  private GenericKubernetesResourceMatcher(KubernetesDependentResource<R, P> dependentResource) {
    this.dependentResource = dependentResource;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static <R extends HasMetadata, P extends HasMetadata> Matcher<R, P> matcherFor(
      Class<R> resourceType, KubernetesDependentResource<R, P> dependentResource) {
    if (Secret.class.isAssignableFrom(resourceType)) {
      return (actual, primary, context) -> {
        final var desired = dependentResource.desired(primary, context);
        return Result.computed(
            ResourceComparators.compareSecretData((Secret) desired, (Secret) actual), desired);
      };
    } else if (ConfigMap.class.isAssignableFrom(resourceType)) {
      return (actual, primary, context) -> {
        final var desired = dependentResource.desired(primary, context);
        return Result.computed(
            ResourceComparators.compareConfigMapData((ConfigMap) desired, (ConfigMap) actual),
            desired);
      };
    } else {
      return new GenericKubernetesResourceMatcher(dependentResource);
    }
  }

  @Override
  public Result<R> match(R actualResource, P primary, Context context) {
    final var objectMapper = context.getConfigurationService().getObjectMapper();
    final var desired = dependentResource.desired(primary, context);

    // reflection will be replaced by this:
    // https://github.com/fabric8io/kubernetes-client/issues/3816
    var desiredSpecNode = objectMapper.valueToTree(ReconcilerUtils.getSpec(desired));
    var actualSpecNode = objectMapper.valueToTree(ReconcilerUtils.getSpec(actualResource));
    var diffJsonPatch = JsonDiff.asJson(desiredSpecNode, actualSpecNode);
    for (int i = 0; i < diffJsonPatch.size(); i++) {
      String operation = diffJsonPatch.get(i).get("op").asText();
      if (!operation.equals("add")) {
        return Result.computed(false, desired);
      }
    }
    return Result.computed(true, desired);
  }
}
