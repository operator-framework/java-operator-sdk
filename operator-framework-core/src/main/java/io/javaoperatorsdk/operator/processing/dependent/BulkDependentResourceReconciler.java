package io.javaoperatorsdk.operator.processing.dependent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

class BulkDependentResourceReconciler<R, P extends HasMetadata>
    implements DependentResourceReconciler<R, P> {

  private final BulkDependentResource<R, P> bulkDependentResource;

  BulkDependentResourceReconciler(BulkDependentResource<R, P> bulkDependentResource) {
    this.bulkDependentResource = bulkDependentResource;
  }

  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    final var targetKeys = bulkDependentResource.desiredResourceKeys(primary, context);
    Map<String, R> actualResources =
        bulkDependentResource.getSecondaryResources(primary, context);

    deleteBulkResourcesIfRequired(targetKeys, actualResources, primary, context);

    final List<ReconcileResult<R>> results = new ArrayList<>(targetKeys.size());
    actualResources.forEach((key, value) -> {
      final var instance = new BulkDependentResourceInstance<>(key, bulkDependentResource);
      results.add(instance.reconcile(primary, value, context));
    });

    return ReconcileResult.aggregatedResult(results);
  }

  @Override
  public void delete(P primary, Context<P> context) {
    var actualResources = bulkDependentResource.getSecondaryResources(primary, context);
    deleteBulkResourcesIfRequired(Collections.emptySet(), actualResources, primary, context);
  }

  protected void deleteBulkResourcesIfRequired(Set<String> expectedKeys,
      Map<String, R> actualResources, P primary, Context<P> context) {
    actualResources.forEach((key, value) -> {
      if (!expectedKeys.contains(key)) {
        bulkDependentResource.deleteBulkResource(primary, value, key, context);
      }
    });
  }

  private static class BulkDependentResourceInstance<R, P extends HasMetadata>
      extends AbstractDependentResource<R, P> {
    private final String key;
    private final BulkDependentResource<R, P> bulkDependentResource;

    private BulkDependentResourceInstance(String key,
        BulkDependentResource<R, P> bulkDependentResource) {
      this.key = key;
      this.bulkDependentResource = bulkDependentResource;
    }

    @SuppressWarnings("unchecked")
    private AbstractDependentResource<R, P> asAbstractDependentResource() {
      return (AbstractDependentResource<R, P>) bulkDependentResource;
    }

    @Override
    protected R desired(P primary, Context<P> context) {
      return bulkDependentResource.desired(primary, key, context);
    }

    @Override
    public Result<R> match(R resource, P primary, Context<P> context) {
      return bulkDependentResource.match(resource, primary, key, context);
    }

    @Override
    protected void onCreated(ResourceID primaryResourceId, R created) {
      asAbstractDependentResource().onCreated(primaryResourceId, created);
    }

    @Override
    protected void onUpdated(ResourceID primaryResourceId, R updated, R actual) {
      asAbstractDependentResource().onUpdated(primaryResourceId, updated, actual);
    }

    @Override
    public Class<R> resourceType() {
      return asAbstractDependentResource().resourceType();
    }
  }
}
