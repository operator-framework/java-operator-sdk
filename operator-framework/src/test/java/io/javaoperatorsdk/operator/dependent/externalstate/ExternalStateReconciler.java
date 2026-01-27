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
package io.javaoperatorsdk.operator.dependent.externalstate;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.ReconcileUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceStartPriority;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingConfigurationBuilder;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalResource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class ExternalStateReconciler
    implements Reconciler<ExternalStateCustomResource>,
        Cleaner<ExternalStateCustomResource>,
        TestExecutionInfoProvider {

  public static final String ID_KEY = "id";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  InformerEventSource<ConfigMap, ExternalStateCustomResource> configMapEventSource;
  PerResourcePollingEventSource<ExternalResource, ExternalStateCustomResource, String>
      externalResourceEventSource;

  @Override
  public UpdateControl<ExternalStateCustomResource> reconcile(
      ExternalStateCustomResource resource, Context<ExternalStateCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    var externalResource = context.getSecondaryResource(ExternalResource.class);
    externalResource.ifPresentOrElse(
        r -> {
          if (!r.getData().equals(resource.getSpec().getData())) {
            updateExternalResource(resource, r, context);
          }
        },
        () -> {
          if (externalResource.isEmpty()) {
            createExternalResource(resource, context);
          }
        });

    return UpdateControl.noUpdate();
  }

  private void updateExternalResource(
      ExternalStateCustomResource resource,
      ExternalResource externalResource,
      Context<ExternalStateCustomResource> context) {
    var newResource = new ExternalResource(externalResource.getId(), resource.getSpec().getData());
    externalService.update(newResource);
    externalResourceEventSource.handleRecentResourceUpdate(
        ResourceID.fromResource(resource), newResource, externalResource);
  }

  private void createExternalResource(
      ExternalStateCustomResource resource, Context<ExternalStateCustomResource> context) {
    var createdResource =
        externalService.create(new ExternalResource(resource.getSpec().getData()));
    var configMap =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(resource.getMetadata().getName())
                    .withNamespace(resource.getMetadata().getNamespace())
                    .build())
            .withData(Map.of(ID_KEY, createdResource.getId()))
            .build();
    configMap.addOwnerReference(resource);

    var primaryID = ResourceID.fromResource(resource);
    // Making sure that the created resources are in the cache for the next reconciliation.
    // This is critical in this case, since on next reconciliation if it would not be in the cache
    // it would be created again.
    configMapEventSource.eventFilteringUpdateAndCacheResource(
        configMap, toCreate -> ReconcileUtils.serverSideApply(context, toCreate));
    externalResourceEventSource.handleRecentResourceCreate(primaryID, createdResource);
  }

  @Override
  public DeleteControl cleanup(
      ExternalStateCustomResource resource, Context<ExternalStateCustomResource> context) {
    var externalResource = context.getSecondaryResource(ExternalResource.class);
    externalResource.ifPresent(er -> externalService.delete(er.getId()));
    context
        .getClient()
        .configMaps()
        .inNamespace(resource.getMetadata().getNamespace())
        .withName(resource.getMetadata().getName())
        .delete();
    return DeleteControl.defaultDelete();
  }

  @Override
  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource<?, ExternalStateCustomResource>> prepareEventSources(
      EventSourceContext<ExternalStateCustomResource> context) {

    configMapEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, ExternalStateCustomResource.class)
                .build(),
            context);
    configMapEventSource.setEventSourcePriority(EventSourceStartPriority.RESOURCE_STATE_LOADER);

    final PerResourcePollingEventSource.ResourceFetcher<
            ExternalResource, ExternalStateCustomResource>
        fetcher =
            (ExternalStateCustomResource primaryResource) -> {
              var configMap =
                  configMapEventSource.getSecondaryResource(primaryResource).orElse(null);
              if (configMap == null) {
                return Collections.emptySet();
              }
              var id = configMap.getData().get(ID_KEY);
              var externalResource = externalService.read(id);
              return externalResource.map(Set::of).orElseGet(Collections::emptySet);
            };
    externalResourceEventSource =
        new PerResourcePollingEventSource<ExternalResource, ExternalStateCustomResource, String>(
            ExternalResource.class,
            context,
            new PerResourcePollingConfigurationBuilder<
                    ExternalResource, ExternalStateCustomResource, String>(
                    fetcher, Duration.ofMillis(300L))
                .build());

    return Arrays.asList(configMapEventSource, externalResourceEventSource);
  }
}
