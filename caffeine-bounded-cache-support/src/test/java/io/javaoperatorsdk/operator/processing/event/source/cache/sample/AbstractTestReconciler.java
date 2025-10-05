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
package io.javaoperatorsdk.operator.processing.event.source.cache.sample;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.cache.BoundedItemStore;
import io.javaoperatorsdk.operator.processing.event.source.cache.CaffeineBoundedItemStores;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.clusterscope.BoundedCacheClusterScopeTestReconciler;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestSpec;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestStatus;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public abstract class AbstractTestReconciler<
        P extends CustomResource<BoundedCacheTestSpec, BoundedCacheTestStatus>>
    implements Reconciler<P> {

  private static final Logger log =
      LoggerFactory.getLogger(BoundedCacheClusterScopeTestReconciler.class);

  public static final String DATA_KEY = "dataKey";

  @Override
  public UpdateControl<P> reconcile(P resource, Context<P> context) {
    var maybeConfigMap = context.getSecondaryResource(ConfigMap.class);
    maybeConfigMap.ifPresentOrElse(
        cm -> updateConfigMapIfNeeded(cm, resource, context),
        () -> createConfigMap(resource, context));
    ensureStatus(resource);
    log.info("Reconciled: {}", resource.getMetadata().getName());
    return UpdateControl.patchStatus(resource);
  }

  protected void updateConfigMapIfNeeded(ConfigMap cm, P resource, Context<P> context) {
    var data = cm.getData().get(DATA_KEY);
    if (data == null || data.equals(resource.getSpec().getData())) {
      cm.setData(Map.of(DATA_KEY, resource.getSpec().getData()));
      context.getClient().configMaps().resource(cm).replace();
    }
  }

  protected void createConfigMap(P resource, Context<P> context) {
    var cm =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(resource.getMetadata().getName())
                    .withNamespace(resource.getSpec().getTargetNamespace())
                    .build())
            .withData(Map.of(DATA_KEY, resource.getSpec().getData()))
            .build();
    cm.addOwnerReference(resource);
    context.getClient().configMaps().resource(cm).create();
  }

  @Override
  public List<EventSource<?, P>> prepareEventSources(EventSourceContext<P> context) {

    var boundedItemStore =
        boundedItemStore(
            new KubernetesClientBuilder().build(),
            ConfigMap.class,
            Duration.ofMinutes(1),
            1); // setting max size for testing purposes

    var es =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(ConfigMap.class, primaryClass())
                .withItemStore(boundedItemStore)
                .withSecondaryToPrimaryMapper(
                    Mappers.fromOwnerReferences(
                        context.getPrimaryResourceClass(),
                        this instanceof BoundedCacheClusterScopeTestReconciler))
                .build(),
            context);

    return List.of(es);
  }

  private void ensureStatus(P resource) {
    if (resource.getStatus() == null) {
      resource.setStatus(new BoundedCacheTestStatus());
    }
  }

  public static <R extends HasMetadata> BoundedItemStore<R> boundedItemStore(
      KubernetesClient client,
      Class<R> rClass,
      Duration accessExpireDuration,
      // max size is only for testing purposes
      long cacheMaxSize) {
    Cache<String, R> cache =
        Caffeine.newBuilder()
            .expireAfterAccess(accessExpireDuration)
            .maximumSize(cacheMaxSize)
            .build();
    return CaffeineBoundedItemStores.boundedItemStore(client, rClass, cache);
  }

  protected abstract Class<P> primaryClass();
}
