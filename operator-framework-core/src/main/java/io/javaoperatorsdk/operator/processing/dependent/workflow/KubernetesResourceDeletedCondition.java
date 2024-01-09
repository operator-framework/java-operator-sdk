package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

/**
 * A condition implementation meant to be used as a delete post-condition on Kubernetes dependent
 * resources to prevent the workflow from proceeding until the associated resource is actually
 * deleted from the server (or, at least, doesn't have any finalizers anymore). This is needed in
 * cases where a cleaning process depends on resources being actually removed from the server
 * because, by default, workflows simply request the deletion but do NOT wait for the resources to
 * be actually deleted.
 */
public class KubernetesResourceDeletedCondition implements Condition<HasMetadata, HasMetadata> {

  @Override
  public boolean isMet(DependentResource<HasMetadata, HasMetadata> dependentResource,
      HasMetadata primary, Context<HasMetadata> context) {
    var optionalResource = dependentResource.getSecondaryResource(primary, context);
    if (optionalResource.isEmpty()) {
      return true;
    } else {
      return optionalResource.orElseThrow().getMetadata().getFinalizers().isEmpty();
    }
  }
}
