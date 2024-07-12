package io.javaoperatorsdk.operator.sample.bulkdependent.readonly;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.sample.bulkdependent.BulkDependentTestCustomResource;

public class ReadOnlyBulkReadyPostCondition
    implements Condition<ConfigMap, BulkDependentTestCustomResource> {
  @Override
  public boolean isMet(
      DependentResource<ConfigMap, BulkDependentTestCustomResource> dependentResource,
      BulkDependentTestCustomResource primary, Context<BulkDependentTestCustomResource> context) {
    var minResourceNumber = primary.getSpec().getNumberOfResources();
    @SuppressWarnings("unchecked")
    var secondaryResources =
        ((BulkDependentResource<ConfigMap, BulkDependentTestCustomResource>) dependentResource)
            .getSecondaryResources(primary, context);
    return minResourceNumber <= secondaryResources.size();
  }
}
