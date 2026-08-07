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

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.informers.ExceptionHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;
import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;

/**
 * Base class for the informer pool strategies, and the type the configuration API accepts (see
 * {@link io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider#withInformerPool}),
 * so custom strategies are expected to extend this rather than to implement {@link InformerPool}
 * directly.
 *
 * <p>Creating an informer from an {@link InformerClassifier}, starting it and waiting for its cache
 * to sync, and holding on to the injected {@link ConfigurationService} are handled here. Subclasses
 * are left with the actual strategy: whether an informer is handed out to more than one event
 * source and, consequently, when it is stopped.
 */
@Experimental(API_MIGHT_CHANGE)
public abstract class AbstractInformerPool implements InformerPool {

  private static final Logger log = LoggerFactory.getLogger(AbstractInformerPool.class);

  protected ConfigurationService configurationService;

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  @Override
  public void setConfigurationService(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  /**
   * Number of distinct informers currently held in the pool for the given resource type. With a
   * sharing pool multiple controllers watching the same resource are backed by a single informer
   * (so this returns {@code 1}), whereas a non-sharing pool creates one informer per user.
   */
  public abstract long numberOfInformersForResource(Class<? extends HasMetadata> resourceClass);

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected SharedIndexInformer createInformer(InformerClassifier<?> classifier) {
    var client = classifier.client();

    MixedOperation<?, ?, ?> clientWithResource;
    if (classifier.groupVersionKind() != null) {
      clientWithResource =
          client.genericKubernetesResources(
              classifier.groupVersionKind().getApiVersion(),
              classifier.groupVersionKind().getKind());
    } else {
      clientWithResource = client.resources(classifier.resourceClass());
    }

    FilterWatchListDeletable filteredClient;
    if (WATCH_ALL_NAMESPACES.equals(classifier.namespaceIdentifier())) {
      filteredClient = clientWithResource.inAnyNamespace();
    } else {
      filteredClient = clientWithResource.inNamespace(classifier.namespaceIdentifier());
    }
    filteredClient =
        (FilterWatchListDeletable) filteredClient.withLabelSelector(classifier.labelSelector());
    filteredClient =
        (FilterWatchListDeletable) filteredClient.withShardSelector(classifier.shardSelector());

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

    if (classifier.informerListLimit() != null) {
      filteredClient =
          (FilterWatchListDeletable) filteredClient.withLimit(classifier.informerListLimit());
    }

    var informer = filteredClient.runnableInformer(0);

    Optional.ofNullable(classifier.itemStore()).ifPresent(informer::itemStore);

    configurationService
        .getInformerStoppedHandler()
        .ifPresent(
            ish -> {
              final var stopped = informer.stopped();
              if (stopped != null) {
                stopped.handle(
                    (res, ex) -> {
                      ish.onStop(informer, (Throwable) ex);
                      return null;
                    });
              } else {
                throw new IllegalStateException(
                    "Cannot retrieve 'stopped' callback to listen to informer stopping for"
                        + " informer for "
                        + ReconcilerUtilsInternal.getResourceTypeNameWithVersion(
                            informer.getApiTypeClass()));
              }
            });
    if (!configurationService.stopOnInformerErrorDuringStartup()) {
      informer.exceptionHandler((b, t) -> !ExceptionHandler.isDeserializationException(t));
    }
    return informer;
  }

  @Override
  public <R extends HasMetadata> void start(
      SharedIndexInformer<R> informer, InformerClassifier<R> informerClassifier) {
    // change thread name for easier debugging
    final var thread = Thread.currentThread();
    final var name = thread.getName();
    try {
      thread.setName(
          "InformerInfo[" + informer.getApiTypeClass().getSimpleName() + "] " + thread.getId());
      final var resourceName = informer.getApiTypeClass().getSimpleName();
      var start = informer.start();
      // note that in case we don't put here timeout and stopOnInformerErrorDuringStartup is
      // false, and there is a rbac issue the get never returns; therefore operator never really
      // starts
      log.trace(
          "Waiting informer to start namespace: {} resource: {}",
          informerClassifier.namespaceIdentifier(),
          resourceName);
      start
          .toCompletableFuture()
          .get(configurationService.cacheSyncTimeout().toMillis(), TimeUnit.MILLISECONDS);
      log.debug(
          "Started informer for namespace: {} resource: {}",
          informerClassifier.namespaceIdentifier(),
          resourceName);
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
    } catch (Exception e) {
      ReconcilerUtilsInternal.handleKubernetesClientException(
          e, HasMetadata.getFullResourceName(informer.getApiTypeClass()));
      throw new OperatorException(
          "Couldn't start informer for " + versionedFullResourceName(informer) + " resources", e);
    } finally {
      // restore original name
      thread.setName(name);
    }
  }

  private String versionedFullResourceName(SharedIndexInformer<? extends HasMetadata> informer) {
    final var apiTypeClass = informer.getApiTypeClass();
    if (GenericKubernetesResource.class.isAssignableFrom(apiTypeClass)) {
      return GenericKubernetesResource.class.getSimpleName();
    }
    return ReconcilerUtilsInternal.getResourceTypeNameWithVersion(apiTypeClass);
  }
}
