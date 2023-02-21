package io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope;

import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.AbstractTestReconciler;

@ControllerConfiguration
public class BoundedCacheTestReconciler
    extends AbstractTestReconciler<BoundedCacheTestCustomResource> {

}
