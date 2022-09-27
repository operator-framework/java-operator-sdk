package io.javaoperatorsdk.operator.sample.multiplemanagedexternaldependenttype;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.support.ExternalResource;

public class ExternalDependentResource2 extends AbstractExternalDependentResource {

  public static final String SUFFIX = "-2";

  public ExternalDependentResource2() {
    setResourceDiscriminator(new ExternalResourceDiscriminator(SUFFIX));
  }

  @Override
  protected ExternalResource desired(MultipleManagedExternalDependentResourceCustomResource primary,
      Context<MultipleManagedExternalDependentResourceCustomResource> context) {
    return new ExternalResource(ExternalResource.toExternalResourceId(primary) + SUFFIX,
        primary.getSpec().getValue());
  }
}
