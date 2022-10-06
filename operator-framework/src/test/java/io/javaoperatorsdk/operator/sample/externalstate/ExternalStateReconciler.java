package io.javaoperatorsdk.operator.sample.externalstate;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceStartPriority;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalResource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration()
public class ExternalStateReconciler
    implements Reconciler<ExternalStateCustomResource>, Cleaner<ExternalStateCustomResource>,
    EventSourceInitializer<ExternalStateCustomResource>, KubernetesClientAware,
    TestExecutionInfoProvider {

  public static final String ID_KEY = "id";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();
  private KubernetesClient client;

  InformerEventSource<ConfigMap, ExternalStateCustomResource> configMapEventSource;
  PerResourcePollingEventSource<ExternalResource, ExternalStateCustomResource> externalResourceEventSource;

  @Override
  public UpdateControl<ExternalStateCustomResource> reconcile(
      ExternalStateCustomResource resource, Context<ExternalStateCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    var externalResource = context.getSecondaryResource(ExternalResource.class);
    if (externalResource.isEmpty()) {
      createExternalResource(resource);
    }
    return UpdateControl.noUpdate();
  }

  private void createExternalResource(ExternalStateCustomResource resource) {
    var createdResource =
        externalService.create(new ExternalResource(resource.getMetadata().getName()));
    var configMap = new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build())
        .withData(Map.of(ID_KEY, createdResource.getId()))
        .build();
    configMap.addOwnerReference(resource);
    client.configMaps().resource(configMap).create();

    var primaryID = ResourceID.fromResource(resource);
    configMapEventSource.handleRecentResourceCreate(primaryID, configMap);
    externalResourceEventSource.handleRecentResourceCreate(primaryID, createdResource);
  }

  @Override
  public DeleteControl cleanup(ExternalStateCustomResource resource,
      Context<ExternalStateCustomResource> context) {
    client.configMaps().inNamespace(resource.getMetadata().getNamespace())
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
      var configMap = configMapEventSource.getSecondaryResource(primaryResource).orElseThrow();
      var id = configMap.getData().get(ID_KEY);
      var externalResource = externalService.read(id);
      return externalResource.map(er -> Set.of(er)).orElse(Collections.emptySet());
    }, context.getPrimaryCache(), 300L, ExternalResource.class);

    return EventSourceInitializer.nameEventSources(configMapEventSource,
        externalResourceEventSource);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return client;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
  }
}
