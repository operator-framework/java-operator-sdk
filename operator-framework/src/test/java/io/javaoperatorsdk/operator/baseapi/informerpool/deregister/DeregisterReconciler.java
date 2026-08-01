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
package io.javaoperatorsdk.operator.baseapi.informerpool.deregister;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

/**
 * Dynamically registers an event source for {@link DeregisterWatchedCustomResource} while the
 * primary's spec requests it, and dynamically de-registers it otherwise. Used to verify that
 * de-registering a dynamically registered event source releases the underlying informer from the
 * pool.
 */
@ControllerConfiguration
public class DeregisterReconciler implements Reconciler<DeregisterPrimaryCustomResource> {

  public static final String WATCHED_EVENT_SOURCE_NAME = "deregister-watched-informer";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<DeregisterPrimaryCustomResource> reconcile(
      DeregisterPrimaryCustomResource primary, Context<DeregisterPrimaryCustomResource> context) {
    numberOfExecutions.incrementAndGet();

    if (primary.getSpec() != null && primary.getSpec().isRegisterEventSource()) {
      context.eventSourceRetriever().dynamicallyRegisterEventSource(watchedEventSource(context));
    } else {
      context.eventSourceRetriever().dynamicallyDeRegisterEventSource(WATCHED_EVENT_SOURCE_NAME);
    }

    return UpdateControl.noUpdate();
  }

  private InformerEventSource<DeregisterWatchedCustomResource, DeregisterPrimaryCustomResource>
      watchedEventSource(Context<DeregisterPrimaryCustomResource> context) {
    var config =
        InformerEventSourceConfiguration.from(
                DeregisterWatchedCustomResource.class, DeregisterPrimaryCustomResource.class)
            .withName(WATCHED_EVENT_SOURCE_NAME)
            .withSecondaryToPrimaryMapper(
                (DeregisterWatchedCustomResource watched) ->
                    Set.of(new ResourceID("ignored", watched.getMetadata().getNamespace())))
            .build();
    return new InformerEventSource<>(
        config, context.eventSourceRetriever().eventSourceContextForDynamicRegistration());
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
