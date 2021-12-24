package io.javaoperatorsdk.operator.sample;

import java.util.Set;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.api.config.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceContextAware;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;

public class TomcatDependentResource
    implements DependentResource<Tomcat, Webapp>, PrimaryResourcesRetriever<Tomcat>,
    AssociatedSecondaryResourceIdentifier<Webapp>, EventSourceContextAware<Webapp> {

  private ResourceCache<Webapp> primaryCache;

  @Override
  public void initWith(EventSourceContext<Webapp> context) {
    this.primaryCache = context.getPrimaryCache();
  }

  @Override
  public Set<ResourceID> associatedPrimaryResources(Tomcat t) {
    // To create an event to a related WebApp resource and trigger the reconciliation
    // we need to find which WebApp this Tomcat custom resource is related to.
    // To find the related customResourceId of the WebApp resource we traverse the cache to
    // and identify it based on naming convention.
    return primaryCache
        .list(webApp -> webApp.getSpec().getTomcat().equals(t.getMetadata().getName()))
        .map(ResourceID::fromResource)
        .collect(Collectors.toSet());
  }

  @Override
  public ResourceID associatedSecondaryID(Webapp primary) {
    return new ResourceID(primary.getSpec().getTomcat(), primary.getMetadata().getNamespace());
  }
}
