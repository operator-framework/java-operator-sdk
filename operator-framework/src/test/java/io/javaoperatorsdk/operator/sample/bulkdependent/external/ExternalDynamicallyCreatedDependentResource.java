package io.javaoperatorsdk.operator.sample.bulkdependent.external;

import java.util.*;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.DynamicallyCreatedDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.DynamicallyCreatedUpdater;
import io.javaoperatorsdk.operator.processing.dependent.external.PollingDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.sample.bulkdependent.DynamicDependentTestCustomResource;

public class ExternalDynamicallyCreatedDependentResource
    extends PollingDependentResource<ExternalResource, DynamicDependentTestCustomResource>
    implements
    DynamicallyCreatedDependentResource<ExternalResource, DynamicDependentTestCustomResource>,
    DynamicallyCreatedUpdater<ExternalResource, DynamicDependentTestCustomResource> {

  public static final String EXTERNAL_RESOURCE_NAME_DELIMITER = "#";

  private final ExternalServiceMock externalServiceMock = ExternalServiceMock.getInstance();

  public ExternalDynamicallyCreatedDependentResource() {
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
  public ExternalResource create(ExternalResource desired,
      DynamicDependentTestCustomResource primary,
      Context<DynamicDependentTestCustomResource> context) {
    return externalServiceMock.create(desired);
  }

  @Override
  public ExternalResource update(ExternalResource actual, ExternalResource desired,
      DynamicDependentTestCustomResource primary,
      Context<DynamicDependentTestCustomResource> context) {
    return externalServiceMock.update(desired);
  }

  private static String toExternalResourceId(DynamicDependentTestCustomResource primary, String i) {
    return primary.getMetadata().getName() + EXTERNAL_RESOURCE_NAME_DELIMITER +
        primary.getMetadata().getNamespace() +
        EXTERNAL_RESOURCE_NAME_DELIMITER + i;
  }

  private ResourceID toResourceID(ExternalResource externalResource) {
    var parts = externalResource.getId().split(EXTERNAL_RESOURCE_NAME_DELIMITER);
    return new ResourceID(parts[0], parts[1]);
  }

  @Override
  public Map<String, ExternalResource> desiredResources(DynamicDependentTestCustomResource primary,
      Context<DynamicDependentTestCustomResource> context) {
    var number = primary.getSpec().getNumberOfResources();
    Map<String, ExternalResource> res = new HashMap<>();
    for (int i = 0; i < number; i++) {
      var key = Integer.toString(i);
      res.put(key, new ExternalResource(toExternalResourceId(primary, key),
          primary.getSpec().getAdditionalData()));
    }
    return res;
  }

  @Override
  public Map<String, ExternalResource> getSecondaryResources(
      DynamicDependentTestCustomResource primary,
      Context<DynamicDependentTestCustomResource> context) {
    return context.getSecondaryResources(resourceType()).stream()
        .filter(r -> r.getId()
            .startsWith(primary.getMetadata().getName() + EXTERNAL_RESOURCE_NAME_DELIMITER +
                primary.getMetadata().getNamespace() +
                EXTERNAL_RESOURCE_NAME_DELIMITER))
        .collect(Collectors.toMap(
            r -> r.getId().substring(r.getId().lastIndexOf(EXTERNAL_RESOURCE_NAME_DELIMITER) + 1),
            r -> r));
  }

  @Override
  public void deleteTargetResource(DynamicDependentTestCustomResource primary,
      ExternalResource resource,
      Context<DynamicDependentTestCustomResource> context) {
    externalServiceMock.delete(resource.getId());
  }
}
