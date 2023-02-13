package io.javaoperatorsdk.operator.processing.event.source.cache.sample;

import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import io.javaoperatorsdk.operator.processing.event.source.cache.BoundedItemStore;
import io.javaoperatorsdk.operator.processing.event.source.cache.CaffeinBoundedCache;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@ControllerConfiguration
public class BoundedCacheTestReconciler implements Reconciler<BoundedCacheTestCustomResource>,
    EventSourceInitializer<BoundedCacheTestCustomResource>, KubernetesClientAware {

  private static final Logger log = LoggerFactory.getLogger(BoundedCacheTestReconciler.class);

  public static final String DATA_KEY = "dataKey";
  private KubernetesClient client;

  @Override
  public UpdateControl<BoundedCacheTestCustomResource> reconcile(
      BoundedCacheTestCustomResource resource,
      Context<BoundedCacheTestCustomResource> context) {
    var maybeConfigMap = context.getSecondaryResource(ConfigMap.class);
    maybeConfigMap.ifPresentOrElse(
        cm -> updateConfigMapIfNeeded(cm, resource),
        () -> createConfigMap(resource));
    ensureStatus(resource);
    log.info("Reconciled: {}", resource.getMetadata().getName());
    return UpdateControl.patchStatus(resource);
  }

  private void updateConfigMapIfNeeded(ConfigMap cm, BoundedCacheTestCustomResource resource) {
    var data = cm.getData().get(DATA_KEY);
    if (data == null || data.equals(resource.getSpec().getData())) {
      cm.setData(Map.of(DATA_KEY, resource.getSpec().getData()));
      client.configMaps().resource(cm).replace();
    }
  }

  private void createConfigMap(BoundedCacheTestCustomResource resource) {
    var cm = new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build())
        .withData(Map.of(DATA_KEY, resource.getSpec().getData()))
        .build();
    cm.addOwnerReference(resource);
    client.configMaps().resource(cm).create();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<BoundedCacheTestCustomResource> context) {
    Cache<String, ConfigMap> cache = Caffeine.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .maximumSize(1)
        .build();

    BoundedItemStore<ConfigMap> boundedItemStore = new BoundedItemStore<>(client,
        new CaffeinBoundedCache<>(cache), ConfigMap.class);

    var es = new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
        .withItemStore(boundedItemStore)
        .build(), context);

    return EventSourceInitializer.nameEventSources(es);
  }

  private void ensureStatus(BoundedCacheTestCustomResource resource) {
    if (resource.getStatus() == null) {
      resource.setStatus(new BoundedCacheTestStatus());
    }
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
