package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class DesiredEqualsMatcher<R, P extends HasMetadata> implements Matcher<R, P> {

  private final AbstractDependentResource<R, P> abstractDependentResource;

  public DesiredEqualsMatcher(AbstractDependentResource<R, P> abstractDependentResource) {
    this.abstractDependentResource = abstractDependentResource;
  }

  @Override
  public Result match(R actualResource, P primary, Context context) {
    var desired = abstractDependentResource.desired(primary, context);
    return Result.computed(actualResource.equals(desired), desired);
  }
}
