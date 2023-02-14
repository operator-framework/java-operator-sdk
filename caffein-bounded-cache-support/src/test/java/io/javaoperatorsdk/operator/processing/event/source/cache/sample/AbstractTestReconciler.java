package io.javaoperatorsdk.operator.processing.event.source.cache.sample;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.cache.CaffeinBoundedItemStores;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.clusterscope.BoundedCacheClusterScopeTestReconciler;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestSpec;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestStatus;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

public abstract class AbstractTestReconciler<P extends CustomResource<BoundedCacheTestSpec, BoundedCacheTestStatus>>
    implements KubernetesClientAware, Reconciler<P>,
    EventSourceInitializer<P> {

  private static final Logger log =
      LoggerFactory.getLogger(BoundedCacheClusterScopeTestReconciler.class);

  public static final String DATA_KEY = "dataKey";
  public static final String DEFAULT_NAMESPACE = "default";

  protected KubernetesClient client;

  @Override
  public UpdateControl<P> reconcile(
      P resource,
      Context<P> context) {
    var maybeConfigMap = context.getSecondaryResource(ConfigMap.class);
    maybeConfigMap.ifPresentOrElse(
        cm -> updateConfigMapIfNeeded(cm, resource),
        () -> createConfigMap(resource));
    ensureStatus(resource);
    log.info("Reconciled: {}", resource.getMetadata().getName());
    return UpdateControl.patchStatus(resource);
  }

  protected void updateConfigMapIfNeeded(ConfigMap cm, P resource) {
    var data = cm.getData().get(DATA_KEY);
    if (data == null || data.equals(resource.getSpec().getData())) {
      cm.setData(Map.of(DATA_KEY, resource.getSpec().getData()));
      client.configMaps().resource(cm).replace();
    }
  }

  protected void createConfigMap(P resource) {
    var cm = new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource instanceof Namespaced ? resource.getMetadata().getNamespace()
                : DEFAULT_NAMESPACE)
            .build())
        .withData(Map.of(DATA_KEY, resource.getSpec().getData()))
        .build();
    cm.addOwnerReference(resource);
    client.configMaps().resource(cm).create();
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
      EventSourceContext<P> context) {

    var boundedItemStore =
        CaffeinBoundedItemStores.boundedItemStore(new KubernetesClientBuilder().build(),
            ConfigMap.class, Duration.ofMinutes(1),
            1);

    var es = new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
        .withItemStore(boundedItemStore)
        .build(), context);

    return EventSourceInitializer.nameEventSources(es);
  }

  private void ensureStatus(P resource) {
    if (resource.getStatus() == null) {
      resource.setStatus(new BoundedCacheTestStatus());
    }
  }

}
