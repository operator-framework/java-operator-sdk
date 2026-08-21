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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

public class DefaultInformerPool extends AbstractInformerPool {

  private static final Logger log = LoggerFactory.getLogger(DefaultInformerPool.class);

  /** A pooled informer together with the number of event sources currently sharing it. */
  private record PooledInformer(SharedIndexInformer<?> informer, AtomicInteger referenceCount) {}

  private final Map<InformerClassifier<?>, PooledInformer> informers = new HashMap<>();

  @SuppressWarnings("unchecked")
  @Override
  public <R extends HasMetadata> SharedIndexInformer<R> getInformer(
      String controllerName, String name, InformerClassifier<R> classifier) {
    SharedIndexInformer<R> informer;
    synchronized (this) {
      var pooled = informers.get(classifier);
      if (pooled == null) {
        informer = createInformer(classifier);
        informers.put(classifier, new PooledInformer(informer, new AtomicInteger(1)));
        log.debug(
            "Created new pooled informer for classifier: {}. Requested by controller: {}, event"
                + " source: {}",
            classifier,
            controllerName,
            name);
      } else {
        informer = (SharedIndexInformer<R>) pooled.informer();
        informers.keySet().stream()
            .filter(existing -> existing.differsOnlyByInformerListLimit(classifier))
            .findFirst()
            .ifPresent(
                existing ->
                    log.warn(
                        "Reusing informer for classifier {} that differs only by informerListLimit"
                            + " (existing: {}, requested: {}). The existing informerListLimit is"
                            + " kept.",
                        classifier,
                        existing.informerListLimit(),
                        classifier.informerListLimit()));
        var referenceCount = pooled.referenceCount().incrementAndGet();
        log.info(
            "Reusing pooled informer for classifier: {}. Reference count now: {}. Requested by"
                + " controller: {}, event source: {}",
            classifier,
            referenceCount,
            controllerName,
            name);
      }
    }
    return informer;
  }

  @SuppressWarnings("unchecked")
  @Override
  public synchronized <R extends HasMetadata> Optional<SharedIndexInformer<R>> releaseInformer(
      String controllerName, String name, InformerClassifier<R> classifier) {
    var pooled = informers.get(classifier);
    if (pooled == null) {
      log.warn("No informer found in the pool for classifier: {}", classifier);
      return Optional.empty();
    }
    var informer = (SharedIndexInformer<R>) pooled.informer();
    // Only the last controller sharing the informer stops it; the informer is still returned to the
    // caller in every case so it can remove its own event handler from the (possibly still running)
    // shared informer.
    var referenceCount = pooled.referenceCount().decrementAndGet();
    if (referenceCount == 0) {
      informers.remove(classifier);
      informer.stop();
      log.debug(
          "Released and stopped last-referenced pooled informer for classifier: {}. Released by"
              + " controller: {}, event source: {}",
          classifier,
          controllerName,
          name);
    } else {
      log.debug(
          "Released pooled informer for classifier: {}, kept running. Reference count now: {}."
              + " Released by controller: {}, event source: {}",
          classifier,
          referenceCount,
          controllerName,
          name);
    }
    return Optional.of(informer);
  }

  /** Total number of distinct informers currently held in the pool. */
  synchronized int size() {
    return informers.size();
  }

  /**
   * Number of distinct informers currently held in the pool for the given resource type. When
   * multiple controllers share a single informer for a resource, this returns {@code 1} for that
   * resource type regardless of how many controllers use it.
   */
  @Override
  public synchronized long numberOfInformersForResource(
      Class<? extends HasMetadata> resourceClass) {
    return informers.keySet().stream()
        .filter(classifier -> resourceClass.equals(classifier.resourceClass()))
        .count();
  }
}
