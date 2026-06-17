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

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@Sample(
    tldr = "Reconciling a Primary Driven by a Referencing Secondary Custom Resource",
    description =
        """
        A configuration custom resource (the secondary) references a target custom resource (the \
        primary) through a spec field and acts as its input. This reconciler watches those config \
        resources with an InformerEventSource and, on each reconciliation, sets the target's value \
        from the config that currently references it, falling back to a default when none does. A \
        SecondaryToPrimaryMapper that overrides the two-argument variant ensures that when a \
        config's reference moves from one target to another, both the previously and the newly \
        referenced target are reconciled.
        """)
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

    // The framework keeps the primary-to-secondary index up to date on reference changes, so a
    // config is only associated with the target it currently references. We take the value from
    // the referencing config, or fall back to the default when none references this target.
    var value =
        context
            .getSecondaryResource(ConfigCustomResource.class)
            .map(config -> config.getSpec().getValue())
            .orElse(DEFAULT_VALUE);

    target.setStatus(new TargetStatus().setValue(value));
    return UpdateControl.patchStatus(target);
  }
}
