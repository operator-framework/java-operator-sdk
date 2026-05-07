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
package io.javaoperatorsdk.operator.processing.event.source.informer.pool;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.informers.ExceptionHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

public class DefaultInformerPool implements InformerPool {

  private static final Logger log = LoggerFactory.getLogger(DefaultInformerPool.class);

  private final KubernetesClient client;
  private final ConfigurationService configurationService;

  private final Map<InformerClassifier, SharedIndexInformer<?>> informers = new HashMap<>();
  private final Map<SharedIndexInformer<?>, AtomicInteger> counters = new HashMap<>();

  public DefaultInformerPool(KubernetesClient client, ConfigurationService configurationService) {
    this.client = client;
    this.configurationService = configurationService;
  }

  public synchronized SharedIndexInformer<?> getResource(InformerClassifier classifier) {
    var informer = informers.get(classifier);
    if (informer == null) {
      informer = createInformer(client, classifier);
      initInformer(informer, classifier.namespaceIdentifier());
      informers.put(classifier, informer);
      counters.put(informer, new AtomicInteger(1));
    } else {
      counters.get(informer).incrementAndGet();
    }
    return informer;
  }

  public synchronized void releaseResource(SharedIndexInformer<?> informer) {
    var counter = counters.get(informer);
    if (counter != null && counter.decrementAndGet() <= 0) {
      informer.stop();
      counters.remove(informer);
      informers.values().remove(informer);
    } else {
      log.warn("No informer found in the pool.");
    }
  }

  private void initInformer(SharedIndexInformer<?> informer, String namespaceIdentifier) {
    try {
      configurationService
          .getInformerStoppedHandler()
          .ifPresent(
              ish -> {
                final var stopped = informer.stopped();
                if (stopped != null) {
                  stopped.handle(
                      (res, ex) -> {
                        ish.onStop(informer, ex);
                        return null;
                      });
                } else {
                  final var apiTypeClass = informer.getApiTypeClass();
                  final var fullResourceName = HasMetadata.getFullResourceName(apiTypeClass);
                  final var version = HasMetadata.getVersion(apiTypeClass);
                  throw new IllegalStateException(
                      "Cannot retrieve 'stopped' callback to listen to informer stopping for"
                          + " informer for "
                          + fullResourceName
                          + "/"
                          + version);
                }
              });
      if (!configurationService.stopOnInformerErrorDuringStartup()) {
        informer.exceptionHandler((b, t) -> !ExceptionHandler.isDeserializationException(t));
      }
      // change thread name for easier debugging
      final var thread = Thread.currentThread();
      final var name = thread.getName();
      try {
        thread.setName(
            "InformerPool [" + versionedFullResourceName(informer) + "] " + thread.getId());
        final var resourceName = informer.getApiTypeClass().getSimpleName();
        log.debug(
            "Starting informer for namespace: {} resource: {}", namespaceIdentifier, resourceName);
        var start = informer.start();
        // note that in case we don't put here timeout and stopOnInformerErrorDuringStartup is
        // false, and there is a rbac issue the get never returns; therefore operator never really
        // starts
        log.trace(
            "Waiting informer to start namespace: {} resource: {}",
            namespaceIdentifier,
            resourceName);
        start
            .toCompletableFuture()
            .get(configurationService.cacheSyncTimeout().toMillis(), TimeUnit.MILLISECONDS);
        log.debug(
            "Started informer for namespace: {} resource: {}", namespaceIdentifier, resourceName);
      } catch (TimeoutException | ExecutionException e) {
        if (configurationService.stopOnInformerErrorDuringStartup()) {
          log.error("Informer startup error. Operator will be stopped. Informer: {}", informer, e);
          throw new OperatorException(e);
        } else {
          log.warn("Informer startup error. Will periodically retry. Informer: {}", informer, e);
        }
      } catch (InterruptedException e) {
        thread.interrupt();
        throw new IllegalStateException(e);
      } finally {
        // restore original name
        thread.setName(name);
      }
    } catch (Exception e) {
      ReconcilerUtilsInternal.handleKubernetesClientException(
          e, HasMetadata.getFullResourceName(informer.getApiTypeClass()));
      throw new OperatorException(
          "Couldn't start informer for " + versionedFullResourceName(informer) + " resources", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static String versionedFullResourceName(SharedIndexInformer<?> informer) {
    return ReconcilerUtilsInternal.getResourceTypeNameWithVersion(
        (Class<? extends HasMetadata>) informer.getApiTypeClass());
  }

  @SuppressWarnings("rawtypes")
  static SharedIndexInformer<?> createInformer(
      KubernetesClient client, InformerClassifier classifier) {
    FilterWatchListDeletable filteredClient;
    if (WATCH_ALL_NAMESPACES.equals(classifier.namespaceIdentifier())) {
      filteredClient =
          client
              .resources(classifier.resourceClass())
              .inAnyNamespace()
              .withLabelSelector(classifier.labelSelector());
    } else {
      filteredClient =
          client
              .resources(classifier.resourceClass())
              .inNamespace(classifier.namespaceIdentifier())
              .withLabelSelector(classifier.labelSelector());
    }

    if (classifier.fieldSelector() != null && !classifier.fieldSelector().getFields().isEmpty()) {
      for (var f : classifier.fieldSelector().getFields()) {
        if (f.negated()) {
          filteredClient =
              (FilterWatchListDeletable) filteredClient.withoutField(f.path(), f.value());
        } else {
          filteredClient = (FilterWatchListDeletable) filteredClient.withField(f.path(), f.value());
        }
      }
    }
    return filteredClient.runnableInformer(0);
  }
}
