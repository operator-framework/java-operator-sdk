package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.zjsonpatch.JsonDiff;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;

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
  public Result<R> match(R actualResource, P primary, Context<P> context) {
    return match(dependentResource, actualResource, primary, context, false);
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
   */
  public static <R extends HasMetadata, P extends HasMetadata> Result<R> match(
      KubernetesDependentResource<R, P> dependentResource, R actualResource, P primary,
      Context<P> context, boolean considerMetadata) {
    final var desired = dependentResource.desired(primary, context);
    if (considerMetadata) {
      final var desiredMetadata = desired.getMetadata();
      final var actualMetadata = actualResource.getMetadata();
      final var matched =
          Objects.equals(desiredMetadata.getAnnotations(), actualMetadata.getAnnotations()) &&
              Objects.equals(desiredMetadata.getLabels(), actualMetadata.getLabels());
      if (!matched) {
        return Result.computed(false, desired);
      }
    }

    final var objectMapper = ConfigurationServiceProvider.instance().getObjectMapper();

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
