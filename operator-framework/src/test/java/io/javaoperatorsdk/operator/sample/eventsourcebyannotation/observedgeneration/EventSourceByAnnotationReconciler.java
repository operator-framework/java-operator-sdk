package io.javaoperatorsdk.operator.sample.eventsourcebyannotation.observedgeneration;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;

@ControllerConfiguration(
    eventSources = @EventSources(informers = @Informer(resourceType = ConfigMap.class,
        followNamespaceChanges = true)))
public class EventSourceByAnnotationReconciler
    implements Reconciler<EventSourceByAnnotationCustomResource>, KubernetesClientAware {

  private KubernetesClient kubernetesClient;
  private final AtomicInteger numberOfExecution = new AtomicInteger();


  @Override
  public UpdateControl<EventSourceByAnnotationCustomResource> reconcile(
      EventSourceByAnnotationCustomResource resource,
      Context<EventSourceByAnnotationCustomResource> context) {
    numberOfExecution.addAndGet(1);
    if (kubernetesClient.configMaps().inNamespace(resource.getMetadata().getNamespace())
        .withName(resource.getMetadata().getName()).get() == null) {
      kubernetesClient.configMaps().inNamespace(resource.getMetadata().getNamespace())
          .createOrReplace(configMap(resource));
    }
    return UpdateControl.noUpdate();
  }

  private ConfigMap configMap(EventSourceByAnnotationCustomResource primary) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder().withName(primary.getMetadata()
        .getName())
        .withNamespace(primary.getMetadata().getNamespace())
        .build());
    configMap.addOwnerReference(primary);
    configMap.setData(Map.of("key", "value"));
    return configMap;
  }

  public int getNumberOfExecution() {
    return numberOfExecution.get();
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }
}
