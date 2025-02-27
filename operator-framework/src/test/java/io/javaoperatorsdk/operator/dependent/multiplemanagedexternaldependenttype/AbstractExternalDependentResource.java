package io.javaoperatorsdk.operator.dependent.multiplemanagedexternaldependenttype;

import java.util.Map;
import java.util.Set;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.external.PollingDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.support.ExternalResource;
import io.javaoperatorsdk.operator.support.ExternalServiceMock;

public abstract class AbstractExternalDependentResource
    extends PollingDependentResource<
        ExternalResource, MultipleManagedExternalDependentResourceCustomResource>
    implements Creator<ExternalResource, MultipleManagedExternalDependentResourceCustomResource>,
        Updater<ExternalResource, MultipleManagedExternalDependentResourceCustomResource>,
        Deleter<MultipleManagedExternalDependentResourceCustomResource> {

  protected ExternalServiceMock externalServiceMock = ExternalServiceMock.getInstance();

  public AbstractExternalDependentResource() {
    super(ExternalResource.class, ExternalResource::getId);
  }

  @Override
  public Map<ResourceID, Set<ExternalResource>> fetchResources() {
    throw new IllegalStateException("Should not be called");
  }

  @Override
  public ExternalResource create(
      ExternalResource desired,
      MultipleManagedExternalDependentResourceCustomResource primary,
      Context<MultipleManagedExternalDependentResourceCustomResource> context) {
    return externalServiceMock.create(desired);
  }

  @Override
  public ExternalResource update(
      ExternalResource actual,
      ExternalResource desired,
      MultipleManagedExternalDependentResourceCustomResource primary,
      Context<MultipleManagedExternalDependentResourceCustomResource> context) {
    return externalServiceMock.update(desired);
  }

  @Override
  public Matcher.Result<ExternalResource> match(
      ExternalResource actualResource,
      MultipleManagedExternalDependentResourceCustomResource primary,
      Context<MultipleManagedExternalDependentResourceCustomResource> context) {
    var desired = desired(primary, context);
    return Matcher.Result.computed(actualResource.equals(desired), desired);
  }

  @Override
  public void delete(
      MultipleManagedExternalDependentResourceCustomResource primary,
      Context<MultipleManagedExternalDependentResourceCustomResource> context) {
    externalServiceMock.delete(toExternalResourceID(primary));
  }

  protected ExternalResource desired(
      MultipleManagedExternalDependentResourceCustomResource primary,
      Context<MultipleManagedExternalDependentResourceCustomResource> context) {
    return new ExternalResource(toExternalResourceID(primary), primary.getSpec().getValue());
  }

  protected String toExternalResourceID(
      MultipleManagedExternalDependentResourceCustomResource primary) {
    return ExternalResource.toExternalResourceId(primary) + resourceIDSuffix();
  }

  protected abstract String resourceIDSuffix();
}
