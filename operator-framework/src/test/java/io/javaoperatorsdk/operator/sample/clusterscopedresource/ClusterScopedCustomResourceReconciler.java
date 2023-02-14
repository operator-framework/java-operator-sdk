package io.javaoperatorsdk.operator.sample.clusterscopedresource;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

@ControllerConfiguration
public class ClusterScopedCustomResourceReconciler
    implements Reconciler<ClusterScopedCustomResource>,
    KubernetesClientAware, EventSourceInitializer<ClusterScopedCustomResource> {

  public static final String DATA_KEY = "data-key";

  private static final Logger log =
      LoggerFactory.getLogger(ClusterScopedCustomResourceReconciler.class);

  private KubernetesClient client;

  @Override
  public UpdateControl<ClusterScopedCustomResource> reconcile(
      ClusterScopedCustomResource resource, Context<ClusterScopedCustomResource> context) {

    var optionalConfigMap = context.getSecondaryResource(ConfigMap.class);

    optionalConfigMap.ifPresentOrElse(cm -> {
      log.debug("Config map data: {} ", cm.getData());
      if (!resource.getSpec().getData().equals(cm.getData().get(DATA_KEY))) {
        client.configMaps().resource(desired(resource)).replace();
      }
    }, () -> client.configMaps().resource(desired(resource)).create());

    resource.setStatus(new ClusterScopedCustomResourceStatus());
    resource.getStatus().setCreated(true);
    return UpdateControl.patchStatus(resource);
  }

  private ConfigMap desired(ClusterScopedCustomResource resource) {
    var cm = new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getSpec().getTargetNamespace())
            .build())
        .withData(Map.of(DATA_KEY, resource.getSpec().getData()))
        .build();
    cm.addOwnerReference(resource);
    return cm;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return client;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<ClusterScopedCustomResource> context) {
    var ies = new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
        .withSecondaryToPrimaryMapper(Mappers.fromOwnerReference(true))
        .build(), context);
    return EventSourceInitializer.nameEventSources(ies);
  }
}
