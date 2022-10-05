package io.javaoperatorsdk.operator.sample.bulkdependent.external;

import java.util.*;
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
    implements BulkDependentResource<ExternalResource, BulkDependentTestCustomResource, Integer>,
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
  public ExternalResource create(ExternalResource desired, BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    return externalServiceMock.create(desired);
  }

  @Override
  public ExternalResource update(ExternalResource actual, ExternalResource desired,
      BulkDependentTestCustomResource primary, Context<BulkDependentTestCustomResource> context) {
    return externalServiceMock.update(desired);
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
  public Set<Integer> targetKeys(BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    var number = primary.getSpec().getNumberOfResources();
    Set<Integer> res = new HashSet<>();
    for (int i = 0; i < number; i++) {
      res.add(i);
    }
    return res;
  }

  @Override
  public Map<Integer, ExternalResource> getSecondaryResources(
      BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    return context.getSecondaryResources(resourceType()).stream()
        .filter(r -> r.getId()
            .startsWith(primary.getMetadata().getName() + EXTERNAL_RESOURCE_NAME_DELIMITER +
                primary.getMetadata().getNamespace() +
                EXTERNAL_RESOURCE_NAME_DELIMITER))
        .collect(Collectors.toMap(
            r -> Integer.parseInt(
                r.getId().substring(r.getId().lastIndexOf(EXTERNAL_RESOURCE_NAME_DELIMITER) + 1)),
            r -> r));
  }

  @Override
  public ExternalResource desired(BulkDependentTestCustomResource primary, Integer key,
      Context<BulkDependentTestCustomResource> context) {
    return new ExternalResource(toExternalResourceId(primary, key),
        primary.getSpec().getAdditionalData());
  }

  @Override
  public void deleteBulkResource(BulkDependentTestCustomResource primary, ExternalResource resource,
      Integer key,
      Context<BulkDependentTestCustomResource> context) {
    externalServiceMock.delete(resource.getId());
  }

  @Override
  public Matcher.Result<ExternalResource> match(ExternalResource actualResource,
      BulkDependentTestCustomResource primary, Integer index,
      Context<BulkDependentTestCustomResource> context) {
    var desired = desired(primary, index, context);
    return Matcher.Result.computed(desired.equals(actualResource), desired);
  }
}
