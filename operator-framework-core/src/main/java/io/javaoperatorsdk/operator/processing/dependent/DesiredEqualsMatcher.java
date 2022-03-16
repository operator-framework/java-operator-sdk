package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class DesiredEqualsMatcher<R, P extends HasMetadata> implements Matcher<R, P> {

  private final AbstractDependentResource<R, P> abstractDependentResource;

  public DesiredEqualsMatcher(AbstractDependentResource<R, P> abstractDependentResource) {
    this.abstractDependentResource = abstractDependentResource;
  }

  @Override
  public Result<R> match(R actualResource, P primary, Context<P> context) {
    var desired = abstractDependentResource.desired(primary, context);
    return Result.computed(actualResource.equals(desired), desired);
  }
}
