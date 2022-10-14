package io.javaoperatorsdk.operator.sample.externalstatedependent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalResource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration()
public class ExternalStateDependentReconciler
    implements Reconciler<ExternalStateDependentCustomResource>, KubernetesClientAware,
    TestExecutionInfoProvider {

  public static final String ID_KEY = "id";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();
  private KubernetesClient client;

  InformerEventSource<ConfigMap, ExternalStateDependentCustomResource> configMapEventSource;
  PerResourcePollingEventSource<ExternalResource, ExternalStateDependentCustomResource> externalResourceEventSource;

  @Override
  public UpdateControl<ExternalStateDependentCustomResource> reconcile(
      ExternalStateDependentCustomResource resource,
      Context<ExternalStateDependentCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    return UpdateControl.noUpdate();
  }

  private void updateExternalResource(ExternalStateDependentCustomResource resource,
      ExternalResource externalResource) {
    var newResource = new ExternalResource(externalResource.getId(), resource.getSpec().getData());
    externalService.update(newResource);
    externalResourceEventSource.handleRecentResourceUpdate(ResourceID.fromResource(resource),
        newResource, externalResource);
  }

  private void createExternalResource(ExternalStateDependentCustomResource resource) {
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
    client.configMaps().resource(configMap).create();

    var primaryID = ResourceID.fromResource(resource);
    // Making sure that the created resources are in the cache for the next reconciliation.
    // This is critical in this case, since on next reconciliation if it would not be in the cache
    // it would be created again.
    configMapEventSource.handleRecentResourceCreate(primaryID, configMap);
    externalResourceEventSource.handleRecentResourceCreate(primaryID, createdResource);
  }



  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
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
