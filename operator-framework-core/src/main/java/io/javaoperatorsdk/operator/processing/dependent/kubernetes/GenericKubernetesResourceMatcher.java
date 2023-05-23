package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors.GenericResourceUpdatePreProcessor;

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
    return match(desired, actualResource, false, false);
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
  @SuppressWarnings("unchecked")
  public static <R extends HasMetadata> Result<R> match(R desired, R actualResource,
      boolean considerMetadata, boolean equality) {
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

    final ResourceUpdatePreProcessor<R> processor =
        GenericResourceUpdatePreProcessor.processorFor((Class<R>) desired.getClass());
    final var matched = processor.matches(actualResource, desired, equality);
    return Result.computed(matched, desired);
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

  public static <R extends HasMetadata, P extends HasMetadata> Result<R> match(
      KubernetesDependentResource<R, P> dependentResource, R actualResource, P primary,
      Context<P> context, boolean considerMetadata) {
    final var desired = dependentResource.desired(primary, context);
    return match(desired, actualResource, considerMetadata, false);
  }
}
