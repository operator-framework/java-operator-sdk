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
 * Watches the same {@link DynamicSharedInformerThirdCustomResource} ("third" custom resource) as
 * {@link StaticSharedInformerReconciler}, but registers its informer event source
 * <em>dynamically</em> from within {@link #reconcile} instead of statically at startup. Since the
 * configuration matches the static reconciler's, the pool is expected to hand out the same,
 * already-running informer, so the two reconcilers share a single informer for the third resource.
 *
 * <p>The event source is registered while the shared informer is already running and has the
 * pre-existing third resource in its cache. Because the handler is added to an already-running
 * informer, the framework replays the resources already in that informer's cache to this newly
 * added handler on registration, so this reconciler is triggered for the pre-existing third
 * resource. This is asserted by the integration test.
 */
@ControllerConfiguration
public class DynamicSharedInformerReconciler
    implements Reconciler<DynamicSharedInformerPrimaryCustomResource2> {

  public static final String PRIMARY_NAME = "dynamic-primary";
  public static final String THIRD_EVENT_SOURCE_NAME = "dynamic-third-informer";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<DynamicSharedInformerPrimaryCustomResource2> reconcile(
      DynamicSharedInformerPrimaryCustomResource2 resource,
      Context<DynamicSharedInformerPrimaryCustomResource2> context) {
    numberOfExecutions.incrementAndGet();
    context
        .eventSourceRetriever()
        .dynamicallyRegisterEventSource(thirdResourceEventSource(context));
    return UpdateControl.noUpdate();
  }

  private InformerEventSource<
          DynamicSharedInformerThirdCustomResource, DynamicSharedInformerPrimaryCustomResource2>
      thirdResourceEventSource(Context<DynamicSharedInformerPrimaryCustomResource2> context) {
    var config =
        InformerEventSourceConfiguration.from(
                DynamicSharedInformerThirdCustomResource.class,
                DynamicSharedInformerPrimaryCustomResource2.class)
            .withName(THIRD_EVENT_SOURCE_NAME)
            .withSecondaryToPrimaryMapper(
                (DynamicSharedInformerThirdCustomResource third) ->
                    Set.of(new ResourceID(PRIMARY_NAME, third.getMetadata().getNamespace())))
            .build();
    return new InformerEventSource<>(
        config, context.eventSourceRetriever().eventSourceContextForDynamicRegistration());
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
