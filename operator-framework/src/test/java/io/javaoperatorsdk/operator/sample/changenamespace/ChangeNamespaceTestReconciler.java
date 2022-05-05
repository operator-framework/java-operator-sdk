package io.javaoperatorsdk.operator.sample.changenamespace;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class ChangeNamespaceTestReconciler
    implements Reconciler<ChangeNamespaceTestCustomResource>, TestExecutionInfoProvider,
    EventSourceInitializer<ChangeNamespaceTestCustomResource>, KubernetesClientAware {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private KubernetesClient client;

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<ChangeNamespaceTestCustomResource> context) {

    InformerEventSource<ConfigMap, ChangeNamespaceTestCustomResource> configMapES =
        new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
            .build(), context);

    return EventSourceInitializer.nameEventSources(configMapES);
  }

  @Override
  public UpdateControl<ChangeNamespaceTestCustomResource> reconcile(
      ChangeNamespaceTestCustomResource primary,
      Context<ChangeNamespaceTestCustomResource> context) {

    var actualConfigMap = context.getSecondaryResource(ConfigMap.class);
    if (actualConfigMap.isEmpty()) {
      client.configMaps().inNamespace(primary.getMetadata().getNamespace())
          .create(configMap(primary));
    }

    numberOfExecutions.addAndGet(1);

    var statusUpdates = primary.getStatus().getNumberOfStatusUpdates();
    primary.getStatus().setNumberOfStatusUpdates(statusUpdates + 1);
    return UpdateControl.updateStatus(primary);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  private ConfigMap configMap(ChangeNamespaceTestCustomResource primary) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder().withName(primary.getMetadata().getName())
        .withNamespace(primary.getMetadata().getNamespace())
        .build());
    configMap.setData(Map.of("data", primary.getMetadata().getName()));
    configMap.addOwnerReference(primary);
    return configMap;
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
