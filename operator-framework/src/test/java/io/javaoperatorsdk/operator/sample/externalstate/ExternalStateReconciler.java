package io.javaoperatorsdk.operator.sample.externalstate;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceStartPriority;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalResource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class ExternalStateReconciler
    implements Reconciler<ExternalStateCustomResource>, Cleaner<ExternalStateCustomResource>,
    TestExecutionInfoProvider {

  public static final String ID_KEY = "id";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  InformerEventSource<ConfigMap, ExternalStateCustomResource> configMapEventSource;
  PerResourcePollingEventSource<ExternalResource, ExternalStateCustomResource> externalResourceEventSource;

  @Override
  public UpdateControl<ExternalStateCustomResource> reconcile(
      ExternalStateCustomResource resource, Context<ExternalStateCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    var externalResource = context.getSecondaryResource(ExternalResource.class);
    externalResource.ifPresentOrElse(r -> {
      if (!r.getData().equals(resource.getSpec().getData())) {
        updateExternalResource(resource, r, context);
      }
    }, () -> {
      if (externalResource.isEmpty()) {
        createExternalResource(resource, context);
      }
    });


    return UpdateControl.noUpdate();
  }

  private void updateExternalResource(ExternalStateCustomResource resource,
      ExternalResource externalResource, Context<ExternalStateCustomResource> context) {
    var newResource = new ExternalResource(externalResource.getId(), resource.getSpec().getData());
    externalService.update(newResource);
    externalResourceEventSource.handleRecentResourceUpdate(ResourceID.fromResource(resource),
        newResource, externalResource);
  }

  private void createExternalResource(ExternalStateCustomResource resource,
      Context<ExternalStateCustomResource> context) {
    var createdResource =
        externalService.create(new ExternalResource(resource.getSpec().getData()));
    var configMap = new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build())
        .withData(Map.of(ID_KEY, createdResource.getId()))
        .build();
    configMap.addOwnerReference(resource);
    context.getClient().configMaps().resource(configMap).create();

    var primaryID = ResourceID.fromResource(resource);
    // Making sure that the created resources are in the cache for the next reconciliation.
    // This is critical in this case, since on next reconciliation if it would not be in the cache
    // it would be created again.
    configMapEventSource.handleRecentResourceCreate(primaryID, configMap);
    externalResourceEventSource.handleRecentResourceCreate(primaryID, createdResource);
  }

  @Override
  public DeleteControl cleanup(ExternalStateCustomResource resource,
      Context<ExternalStateCustomResource> context) {
    var externalResource = context.getSecondaryResource(ExternalResource.class);
    externalResource.ifPresent(er -> externalService.delete(er.getId()));
    context.getClient().configMaps().inNamespace(resource.getMetadata().getNamespace())
        .withName(resource.getMetadata().getName()).delete();
    return DeleteControl.defaultDelete();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<ExternalStateCustomResource> context) {

    configMapEventSource = new InformerEventSource<>(
        InformerConfiguration.from(ConfigMap.class, context).build(), context);
    configMapEventSource.setEventSourcePriority(EventSourceStartPriority.RESOURCE_STATE_LOADER);

    externalResourceEventSource = new PerResourcePollingEventSource<>(primaryResource -> {
      var configMap = configMapEventSource.getSecondaryResource(primaryResource).orElse(null);
      if (configMap == null) {
        return Collections.emptySet();
      }
      var id = configMap.getData().get(ID_KEY);
      var externalResource = externalService.read(id);
      return externalResource.map(Set::of).orElseGet(Collections::emptySet);
    }, context, Duration.ofMillis(300L), ExternalResource.class);

    return EventSourceUtils.nameEventSources(configMapEventSource,
        externalResourceEventSource);
  }
}
