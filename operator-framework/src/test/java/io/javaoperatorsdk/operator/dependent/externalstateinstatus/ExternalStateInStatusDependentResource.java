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
package io.javaoperatorsdk.operator.dependent.externalstateinstatus;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.external.PerResourcePollingDependentResource;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalResource;

/**
 * Dependent resource managing an external resource in the {@link ExternalIDGenServiceMock fake
 * external service}. The external resource's state - its generated ID - is read from the <b>status
 * </b> of the primary custom resource. The ID is written into the status by {@link
 * ExternalStateInStatusWorkflowReconciler} after the workflow reconciled this dependent, relying on
 * the stronger read-after-write consistency for updates so that the next fetch/reconciliation
 * observes the ID and does not create a duplicate external resource.
 */
public class ExternalStateInStatusDependentResource
    extends PerResourcePollingDependentResource<
        ExternalResource, ExternalStateInStatusWorkflowCustomResource, String>
    implements Creator<ExternalResource, ExternalStateInStatusWorkflowCustomResource>,
        Updater<ExternalResource, ExternalStateInStatusWorkflowCustomResource>,
        Deleter<ExternalStateInStatusWorkflowCustomResource> {

  private final ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  public ExternalStateInStatusDependentResource() {
    super(ExternalResource.class, Duration.ofMillis(300));
  }

  @Override
  public Set<ExternalResource> fetchResources(
      ExternalStateInStatusWorkflowCustomResource primaryResource) {
    return idFromStatus(primaryResource)
        .flatMap(externalService::read)
        .map(Set::of)
        .orElseGet(Collections::emptySet);
  }

  @Override
  protected Optional<ExternalResource> selectTargetSecondaryResource(
      Set<ExternalResource> secondaryResources,
      ExternalStateInStatusWorkflowCustomResource primary,
      Context<ExternalStateInStatusWorkflowCustomResource> context) {
    return idFromStatus(primary)
        .flatMap(id -> secondaryResources.stream().filter(e -> e.getId().equals(id)).findAny());
  }

  @Override
  protected ExternalResource desired(
      ExternalStateInStatusWorkflowCustomResource primary,
      Context<ExternalStateInStatusWorkflowCustomResource> context) {
    return new ExternalResource(primary.getSpec().getData());
  }

  @Override
  public ExternalResource create(
      ExternalResource desired,
      ExternalStateInStatusWorkflowCustomResource primary,
      Context<ExternalStateInStatusWorkflowCustomResource> context) {
    return externalService.create(desired);
  }

  @Override
  public ExternalResource update(
      ExternalResource actual,
      ExternalResource desired,
      ExternalStateInStatusWorkflowCustomResource primary,
      Context<ExternalStateInStatusWorkflowCustomResource> context) {
    return externalService.update(new ExternalResource(actual.getId(), desired.getData()));
  }

  @Override
  public Matcher.Result<ExternalResource> match(
      ExternalResource resource,
      ExternalStateInStatusWorkflowCustomResource primary,
      Context<ExternalStateInStatusWorkflowCustomResource> context) {
    return Matcher.Result.nonComputed(resource.getData().equals(primary.getSpec().getData()));
  }

  @Override
  protected void handleDelete(
      ExternalStateInStatusWorkflowCustomResource primary,
      ExternalResource secondary,
      Context<ExternalStateInStatusWorkflowCustomResource> context) {
    if (secondary != null) {
      externalService.delete(secondary.getId());
    }
  }

  private static Optional<String> idFromStatus(
      ExternalStateInStatusWorkflowCustomResource primary) {
    return Optional.ofNullable(primary.getStatus()).map(ExternalStateInStatusStatus::getId);
  }
}
