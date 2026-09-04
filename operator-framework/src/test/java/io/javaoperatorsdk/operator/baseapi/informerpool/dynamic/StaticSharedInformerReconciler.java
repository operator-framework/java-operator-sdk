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
package io.javaoperatorsdk.operator.baseapi.informerpool.dynamic;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

/**
 * Watches {@link DynamicSharedInformerThirdCustomResource} (the "third" custom resource) as a
 * secondary resource using a <em>statically</em> registered informer event source. Because this
 * event source is registered at startup, its handler is present on the underlying informer before
 * any third resource exists, so this reconciler is triggered by third-resource events. It is the
 * counterpart to {@link DynamicSharedInformerReconciler}, which watches the same third resource but
 * registers its event source dynamically.
 */
@ControllerConfiguration
public class StaticSharedInformerReconciler
    implements Reconciler<DynamicSharedInformerPrimaryCustomResource1> {

  public static final String PRIMARY_NAME = "static-primary";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public List<EventSource<?, DynamicSharedInformerPrimaryCustomResource1>> prepareEventSources(
      EventSourceContext<DynamicSharedInformerPrimaryCustomResource1> context) {
    var config =
        InformerEventSourceConfiguration.from(
                DynamicSharedInformerThirdCustomResource.class,
                DynamicSharedInformerPrimaryCustomResource1.class)
            .withSecondaryToPrimaryMapper(
                (DynamicSharedInformerThirdCustomResource third) ->
                    Set.of(new ResourceID(PRIMARY_NAME, third.getMetadata().getNamespace())))
            .build();
    return List.of(new InformerEventSource<>(config));
  }

  @Override
  public UpdateControl<DynamicSharedInformerPrimaryCustomResource1> reconcile(
      DynamicSharedInformerPrimaryCustomResource1 resource,
      Context<DynamicSharedInformerPrimaryCustomResource1> context) {
    numberOfExecutions.incrementAndGet();
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
