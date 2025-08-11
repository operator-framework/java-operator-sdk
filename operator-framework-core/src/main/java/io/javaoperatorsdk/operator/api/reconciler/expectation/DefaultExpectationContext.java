package io.javaoperatorsdk.operator.api.reconciler.expectation;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.DefaultCacheAware;
import io.javaoperatorsdk.operator.processing.Controller;

public class DefaultExpectationContext<P extends HasMetadata> extends DefaultCacheAware<P>
    implements ExpectationContext<P> {
  public DefaultExpectationContext(Controller<P> controller, P primaryResource) {
    super(controller, primaryResource);
  }
}
