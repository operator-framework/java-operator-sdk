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
package io.javaoperatorsdk.operator.baseapi.externalstateinstatus;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingConfigurationBuilder;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalResource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

/**
 * Manages an external resource (living in the {@link ExternalIDGenServiceMock fake external
 * service}) while storing the external resource's state - here just its generated ID - directly in
 * the <b>status</b> of the custom resource.
 *
 * <p>This pattern only works reliably thanks to the stronger read-after-write consistency for
 * updates: after the external resource is created, its ID is persisted through {@link
 * UpdateControl#patchStatus(io.fabric8.kubernetes.api.model.HasMetadata)}. The patched resource
 * (holding the ID) is placed into the controller's cache, so the very next reconciliation observes
 * the ID and does not create a duplicate external resource - even before the informer delivers the
 * update event.
 */
@ControllerConfiguration
public class ExternalStateInStatusReconciler
    implements Reconciler<ExternalStateInStatusCustomResource>,
        Cleaner<ExternalStateInStatusCustomResource>,
        TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  PerResourcePollingEventSource<ExternalResource, ExternalStateInStatusCustomResource, String>
      externalResourceEventSource;

  @Override
  public UpdateControl<ExternalStateInStatusCustomResource> reconcile(
      ExternalStateInStatusCustomResource resource,
      Context<ExternalStateInStatusCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    var externalResource = context.getSecondaryResource(ExternalResource.class);
    if (externalResource.isEmpty()) {
      // No external resource is associated with this primary yet. If we already stored an ID we
      // are just waiting for the poll to catch up (do nothing), otherwise we create the external
      // resource and persist its ID into the status. Relying on read-after-write consistency the
      // stored ID is visible on the next reconciliation, so no duplicate is created.
      if (idFromStatus(resource) == null) {
        return createExternalResource(resource);
      }
      return UpdateControl.noUpdate();
    }

    var currentExternalResource = externalResource.orElseThrow();
    if (!currentExternalResource.getData().equals(resource.getSpec().getData())) {
      updateExternalResource(resource, currentExternalResource);
    }
    return UpdateControl.noUpdate();
  }

  private UpdateControl<ExternalStateInStatusCustomResource> createExternalResource(
      ExternalStateInStatusCustomResource resource) {
    var createdResource =
        externalService.create(new ExternalResource(resource.getSpec().getData()));

    // Make sure the freshly created external resource is available in the poll cache for the next
    // reconciliation, so it is not created again.
    externalResourceEventSource.handleRecentResourceCreate(
        ResourceID.fromResource(resource), createdResource);

    resource.setStatus(new ExternalStateInStatusStatus().setId(createdResource.getId()));
    return UpdateControl.patchStatus(resource);
  }

  private void updateExternalResource(
      ExternalStateInStatusCustomResource resource, ExternalResource externalResource) {
    var newResource = new ExternalResource(externalResource.getId(), resource.getSpec().getData());
    externalService.update(newResource);
    externalResourceEventSource.handleRecentResourceUpdate(
        ResourceID.fromResource(resource), newResource, externalResource);
  }

  @Override
  public DeleteControl cleanup(
      ExternalStateInStatusCustomResource resource,
      Context<ExternalStateInStatusCustomResource> context) {
    var id = idFromStatus(resource);
    if (id != null) {
      externalService.delete(id);
    }
    return DeleteControl.defaultDelete();
  }

  @Override
  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource<?, ExternalStateInStatusCustomResource>> prepareEventSources(
      EventSourceContext<ExternalStateInStatusCustomResource> context) {

    final PerResourcePollingEventSource.ResourceFetcher<
            ExternalResource, ExternalStateInStatusCustomResource>
        fetcher =
            (ExternalStateInStatusCustomResource primaryResource) -> {
              var id = idFromStatus(primaryResource);
              if (id == null) {
                return Collections.emptySet();
              }
              return externalService.read(id).map(Set::of).orElseGet(Collections::emptySet);
            };
    externalResourceEventSource =
        new PerResourcePollingEventSource<>(
            ExternalResource.class,
            context,
            new PerResourcePollingConfigurationBuilder<
                    ExternalResource, ExternalStateInStatusCustomResource, String>(
                    fetcher, Duration.ofMillis(300L))
                .build());

    return List.of(externalResourceEventSource);
  }

  private static String idFromStatus(ExternalStateInStatusCustomResource resource) {
    return resource.getStatus() == null ? null : resource.getStatus().getId();
  }
}
