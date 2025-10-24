/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.dependent.bulkdependent.external;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.BulkUpdater;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.external.PollingDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ExternalBulkDependentResource
    extends PollingDependentResource<ExternalResource, BulkDependentTestCustomResource, String>
    implements BulkDependentResource<ExternalResource, BulkDependentTestCustomResource, String>,
        Creator<ExternalResource, BulkDependentTestCustomResource>,
        Deleter<BulkDependentTestCustomResource>,
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
    resources.forEach(
        er -> {
          var resourceID = toResourceID(er);
          result.putIfAbsent(resourceID, new HashSet<>());
          result.get(resourceID).add(er);
        });
    return result;
  }

  @Override
  public ExternalResource create(
      ExternalResource desired,
      BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    return externalServiceMock.create(desired);
  }

  @Override
  public ExternalResource update(
      ExternalResource actual,
      ExternalResource desired,
      BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    return externalServiceMock.update(desired);
  }

  private static String toExternalResourceId(BulkDependentTestCustomResource primary, String i) {
    return primary.getMetadata().getName()
        + EXTERNAL_RESOURCE_NAME_DELIMITER
        + primary.getMetadata().getNamespace()
        + EXTERNAL_RESOURCE_NAME_DELIMITER
        + i;
  }

  private ResourceID toResourceID(ExternalResource externalResource) {
    var parts = externalResource.getId().split(EXTERNAL_RESOURCE_NAME_DELIMITER);
    return new ResourceID(parts[0], parts[1]);
  }

  @Override
  public Map<String, ExternalResource> desiredResources(
      BulkDependentTestCustomResource primary, Context<BulkDependentTestCustomResource> context) {
    var number = primary.getSpec().getNumberOfResources();
    Map<String, ExternalResource> res = new HashMap<>();
    for (int i = 0; i < number; i++) {
      var key = Integer.toString(i);
      var resource =
          new ExternalResource(
              toExternalResourceId(primary, key), primary.getSpec().getAdditionalData());
      res.put(getResourceIDMapper().idFor(resource), resource);
    }
    return res;
  }

  @Override
  public Map<String, ExternalResource> getSecondaryResources(
      BulkDependentTestCustomResource primary, Context<BulkDependentTestCustomResource> context) {
    return context
        .getSecondaryResourcesAsStream(resourceType())
        .filter(
            r ->
                r.getId()
                    .startsWith(
                        primary.getMetadata().getName()
                            + EXTERNAL_RESOURCE_NAME_DELIMITER
                            + primary.getMetadata().getNamespace()
                            + EXTERNAL_RESOURCE_NAME_DELIMITER))
        .collect(Collectors.toMap(r -> getResourceIDMapper().idFor(r), r -> r));
  }

  @Override
  public void deleteTargetResource(
      BulkDependentTestCustomResource primary,
      ExternalResource resource,
      String key,
      Context<BulkDependentTestCustomResource> context) {
    externalServiceMock.delete(resource.getId());
  }
}
