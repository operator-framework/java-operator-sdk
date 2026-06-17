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
package io.javaoperatorsdk.operator.baseapi.secondarytoprimaryreferencechange;

import java.util.List;

import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

/**
 * Reconciles {@link TargetCustomResource}s. The desired status value comes from a {@link
 * ConfigCustomResource} that references the target via {@code spec.targetName}; if no config
 * references the target, {@link #DEFAULT_VALUE} is used.
 *
 * <p>{@link ConfigCustomResource}s are watched as secondary resources through an {@link
 * InformerEventSource} configured with {@link ConfigToTargetMapper}, so changes to a config trigger
 * reconciliation of the referenced target(s).
 */
@ControllerConfiguration
public class TargetReconciler implements Reconciler<TargetCustomResource> {

  public static final String DEFAULT_VALUE = "default";

  @Override
  public List<EventSource<?, TargetCustomResource>> prepareEventSources(
      EventSourceContext<TargetCustomResource> context) {

    var configuration =
        InformerEventSourceConfiguration.from(
                ConfigCustomResource.class, TargetCustomResource.class)
            .withSecondaryToPrimaryMapper(new ConfigToTargetMapper())
            .build();

    var ies = new InformerEventSource<>(configuration, context);
    return List.of(ies);
  }

  @Override
  public UpdateControl<TargetCustomResource> reconcile(
      TargetCustomResource target, Context<TargetCustomResource> context) {

    // There may be a stale secondary mapping after a reference change (the primary-to-secondary
    // index does not remove the old association), so we also verify here that the config actually
    // references this target. This makes reconciliation robust regardless of how the event arrived.
    var value =
        context.getSecondaryResources(ConfigCustomResource.class).stream()
            .filter(
                config -> target.getMetadata().getName().equals(config.getSpec().getTargetName()))
            .map(config -> config.getSpec().getValue())
            .findFirst()
            .orElse(DEFAULT_VALUE);

    target.setStatus(new TargetStatus().setValue(value));
    return UpdateControl.patchStatus(target);
  }
}
