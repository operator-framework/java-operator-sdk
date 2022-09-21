package io.javaoperatorsdk.operator.sample.bulkdependent.external;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.*;
import io.javaoperatorsdk.operator.processing.dependent.external.PollingDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.sample.bulkdependent.BulkDependentTestCustomResource;

public class ExternalBulkDependentResource
    extends PollingDependentResource<ExternalResource, BulkDependentTestCustomResource>
    implements BulkDependentResource<ExternalResource, BulkDependentTestCustomResource>,
    BulkUpdater<ExternalResource, BulkDependentTestCustomResource> {

  public static final String EXTERNAL_RESOURCE_NAME_DELIMITER = "#";

  private ExternalServiceMock externalServiceMock = ExternalServiceMock.getInstance();

  public ExternalBulkDependentResource() {
    super(ExternalResource.class, CacheKeyMapper.singleResourceCacheKeyMapper());
  }

  @Override
  public Map<ResourceID, Set<ExternalResource>> fetchResources() {
    // todo
    Map<ResourceID, Set<ExternalResource>> result = new HashMap<>();
    var resources = externalServiceMock.listResources();

    return result;
  }

  @Override
  public void delete(BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    deleteBulkResourcesIfRequired(0, lastKnownBulkSize(), primary, context);
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
  public BulkResourceDiscriminatorFactory<ExternalResource, BulkDependentTestCustomResource> bulkResourceDiscriminatorFactory() {
    return index -> (resource, primary, context) -> {
      ExternalResource collect = context.getSecondaryResources(resource).stream()
          .filter(r -> r.getId().endsWith(EXTERNAL_RESOURCE_NAME_DELIMITER + index))
          .collect(Collectors.collectingAndThen(
              Collectors.toList(),
              list -> {
                if (list.size() > 1) {
                  throw new IllegalStateException("Found more than 1 object: " + list);
                }
                return list.get(0);
              }));
      return Optional.ofNullable(collect);
    };
  }

  @Override
  public ExternalResource desired(BulkDependentTestCustomResource primary, int index,
      Context<BulkDependentTestCustomResource> context) {
    return new ExternalResource(toExternalResourceId(primary, index), "" + index);
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
}
