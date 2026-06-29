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
  private InformerEventSource<StatusEventSecondaryResource, StatusEventPrimaryResource>
      secondaryEventSource;

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

    for (var secondary : secondaries) {
      var generation = secondary.getMetadata().getGeneration();
      var observedGeneration = secondary.getStatus().getObservedGeneration();

      if (!Objects.equals(generation, observedGeneration)) {
        log.info(
            "Patching secondary '{}' status: obsGen {} -> {}",
            secondary.getMetadata().getName(),
            observedGeneration,
            generation);

        secondaryEventSource.eventFilteringUpdateAndCacheResource(
            secondary,
            s -> {
              s.getStatus().setObservedGeneration(s.getMetadata().getGeneration());
              return context.getClient().resource(s).patchStatus();
            });
      }
    }

    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, StatusEventPrimaryResource>> prepareEventSources(
      EventSourceContext<StatusEventPrimaryResource> context) {

    var config =
        InformerEventSourceConfiguration.from(
                StatusEventSecondaryResource.class, StatusEventPrimaryResource.class)
            .withSecondaryToPrimaryMapper(
                secondary -> {
                  var generation = secondary.getMetadata().getGeneration();
                  var observedGeneration = secondary.getStatus().getObservedGeneration();

                  if (!Objects.equals(generation, observedGeneration)) {
                    log.info(
                        "Mapper: secondary '{}' gen {} != obsGen {} -> skipping",
                        secondary.getMetadata().getName(),
                        generation,
                        observedGeneration);
                    return Set.of();
                  }

                  var primaryName = secondary.getMetadata().getLabels().get(PRIMARY_NAME_LABEL);
                  log.info(
                      "Mapper: secondary '{}' gen matches -> mapping to primary '{}'",
                      secondary.getMetadata().getName(),
                      primaryName);
                  return Set.of(
                      new ResourceID(primaryName, secondary.getMetadata().getNamespace()));
                })
            .build();

    secondaryEventSource = new InformerEventSource<>(config, context);
    return List.of(secondaryEventSource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
