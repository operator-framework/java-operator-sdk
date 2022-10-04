package io.javaoperatorsdk.operator.sample.multiplemanagedexternaldependenttype;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.support.ExternalResource;

public class ExternalResourceDiscriminator implements
    ResourceDiscriminator<ExternalResource, MultipleManagedExternalDependentResourceCustomResource> {

  private String suffix;

  public ExternalResourceDiscriminator(String suffix) {
    this.suffix = suffix;
  }

  @Override
  public Optional<ExternalResource> distinguish(Class<ExternalResource> resource,
      MultipleManagedExternalDependentResourceCustomResource primary,
      Context<MultipleManagedExternalDependentResourceCustomResource> context) {
    var resources = context.getSecondaryResources(ExternalResource.class);
    return resources.stream().filter(r -> r.getId().endsWith(suffix)).findFirst();
  }
}
