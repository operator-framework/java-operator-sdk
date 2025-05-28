package io.javaoperatorsdk.operator.processing.event.source.cache.sample.clusterscope;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.AbstractTestReconciler;

@ControllerConfiguration
public class BoundedCacheClusterScopeTestReconciler
    extends AbstractTestReconciler<BoundedCacheClusterScopeTestCustomResource> {

  @Override
  protected Class<BoundedCacheClusterScopeTestCustomResource> primaryClass() {
    return BoundedCacheClusterScopeTestCustomResource.class;
  }
}
