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
package io.javaoperatorsdk.operator.baseapi.statuseventfiltering;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class StatusEventPrimaryReconciler implements Reconciler<StatusEventPrimaryResource> {

  private static final Logger log = LoggerFactory.getLogger(StatusEventPrimaryReconciler.class);

  public static final String PRIMARY_NAME_LABEL = "primary-name";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private volatile CountDownLatch stallLatch;

  public void stallReconcile() {
    stallLatch = new CountDownLatch(1);
  }

  public void releaseReconcile() {
    var latch = stallLatch;
    if (latch != null) {
      latch.countDown();
    }
    stallLatch = null;
  }

  @Override
  public UpdateControl<StatusEventPrimaryResource> reconcile(
      StatusEventPrimaryResource resource, Context<StatusEventPrimaryResource> context) {

    int count = numberOfExecutions.incrementAndGet();
    log.info(
        "PrimaryReconciler reconciled '{}', execution #{}",
        resource.getMetadata().getName(),
        count);

    var secondaries =
        context
            .getClient()
            .resources(StatusEventSecondaryResource.class)
            .inNamespace(resource.getMetadata().getNamespace())
            .withLabel(PRIMARY_NAME_LABEL, resource.getMetadata().getName())
            .list()
            .getItems();
    var latch = stallLatch;
    if (latch != null) {
      try {
        log.info("PrimaryReconciler stalled before returning patchStatus...");
        latch.await(60, TimeUnit.SECONDS);
        log.info("PrimaryReconciler released, returning patchStatus");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    boolean allReady =
        !secondaries.isEmpty()
            && secondaries.stream()
                .allMatch(
                    s ->
                        Objects.equals(
                            s.getMetadata().getGeneration(),
                            s.getStatus().getObservedGeneration()));

    log.info("PrimaryReconciler: secondaryReady={}, secondaries={}", allReady, secondaries.size());

    resource.getStatus().setSecondaryReady(allReady);
    return UpdateControl.patchStatus(resource);
  }

  @Override
  public List<EventSource<?, StatusEventPrimaryResource>> prepareEventSources(
      EventSourceContext<StatusEventPrimaryResource> context) {

    var config =
        InformerEventSourceConfiguration.from(
                StatusEventSecondaryResource.class, StatusEventPrimaryResource.class)
            .withSecondaryToPrimaryMapper(
                secondary -> {
                  var primaryName = secondary.getMetadata().getLabels().get(PRIMARY_NAME_LABEL);
                  if (primaryName == null) {
                    return Set.of();
                  }
                  log.info(
                      "Mapper: secondary '{}' -> primary '{}'",
                      secondary.getMetadata().getName(),
                      primaryName);
                  return Set.of(
                      new ResourceID(primaryName, secondary.getMetadata().getNamespace()));
                })
            .build();

    return List.of(new InformerEventSource<>(config, context));
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
