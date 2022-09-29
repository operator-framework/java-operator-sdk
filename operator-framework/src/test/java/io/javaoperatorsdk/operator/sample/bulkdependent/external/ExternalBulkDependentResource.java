package io.javaoperatorsdk.operator.sample.bulkdependent.external;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.BulkUpdater;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.external.PollingDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.sample.bulkdependent.BulkDependentTestCustomResource;

public class ExternalBulkDependentResource
    extends PollingDependentResource<ExternalResource, BulkDependentTestCustomResource>
    implements BulkDependentResource<ExternalResource, BulkDependentTestCustomResource>,
    BulkUpdater<ExternalResource, BulkDependentTestCustomResource> {

  public static final String EXTERNAL_RESOURCE_NAME_DELIMITER = "#";

  private final ExternalServiceMock externalServiceMock = ExternalServiceMock.getInstance();

  public ExternalBulkDependentResource() {
    super(ExternalResource.class, ExternalResource::getId);
  }

  @Override
  public Map<ResourceID, Set<ExternalResource>> fetchResources() {
    Map<ResourceID, Set<ExternalResource>> result = new HashMap<>();
    var resources = externalServiceMock.listResources();
    resources.forEach(er -> {
      var resourceID = toResourceID(er);
      result.putIfAbsent(resourceID, new HashSet<>());
      result.get(resourceID).add(er);
    });
    return result;
  }

  @Override
  public void delete(BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    deleteBulkResourcesIfRequired(0, primary, context);
  }

  @Override
  public int count(BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    return primary.getSpec().getNumberOfResources();
  }

  @Override
  public void deleteBulkResourceWithIndex(BulkDependentTestCustomResource primary,
      ExternalResource resource, int i, Context<BulkDependentTestCustomResource> context) {
    externalServiceMock.delete(resource.getId());
  }

  @Override
  public ExternalResource desired(BulkDependentTestCustomResource primary, int index,
      Context<BulkDependentTestCustomResource> context) {
    return new ExternalResource(toExternalResourceId(primary, index),
        primary.getSpec().getAdditionalData());
  }

  @Override
  public ExternalResource create(ExternalResource desired, BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    return externalServiceMock.create(desired);
  }

  @Override
  public ExternalResource update(ExternalResource actual, ExternalResource desired,
      BulkDependentTestCustomResource primary, Context<BulkDependentTestCustomResource> context) {
    return externalServiceMock.update(desired);
  }

  @Override
  public Matcher.Result<ExternalResource> match(ExternalResource actualResource,
      BulkDependentTestCustomResource primary,
      int index, Context<BulkDependentTestCustomResource> context) {
    var desired = desired(primary, index, context);
    return Matcher.Result.computed(desired.equals(actualResource), desired);
  }

  private static String toExternalResourceId(BulkDependentTestCustomResource primary, int i) {
    return primary.getMetadata().getName() + EXTERNAL_RESOURCE_NAME_DELIMITER +
        primary.getMetadata().getNamespace() +
        EXTERNAL_RESOURCE_NAME_DELIMITER + i;
  }

  private ResourceID toResourceID(ExternalResource externalResource) {
    var parts = externalResource.getId().split(EXTERNAL_RESOURCE_NAME_DELIMITER);
    return new ResourceID(parts[0], parts[1]);
  }

  @Override
  public Optional<ExternalResource> getSecondaryResource(BulkDependentTestCustomResource primary,
      int index, Context<BulkDependentTestCustomResource> context) {
    return context.getSecondaryResources(resourceType()).stream()
        .filter(r -> r.getId().endsWith(EXTERNAL_RESOURCE_NAME_DELIMITER + index))
        .collect(Collectors.toList()).stream().findFirst();
  }
}
