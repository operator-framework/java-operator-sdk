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
package io.javaoperatorsdk.operator.baseapi.informerpool.basic;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

/**
 * Watches {@link ConfigMap} as a secondary resource with the same configuration as {@link
 * SharedInformerReconciler1} so that both controllers share a single underlying informer from the
 * informer pool.
 */
@ControllerConfiguration
public class SharedInformerReconciler2 implements Reconciler<SharedInformerCustomResource2> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public List<EventSource<?, SharedInformerCustomResource2>> prepareEventSources(
      EventSourceContext<SharedInformerCustomResource2> context) {
    var config =
        InformerEventSourceConfiguration.from(ConfigMap.class, SharedInformerCustomResource2.class)
            .build();
    return List.of(new InformerEventSource<>(config, context));
  }

  @Override
  public UpdateControl<SharedInformerCustomResource2> reconcile(
      SharedInformerCustomResource2 resource, Context<SharedInformerCustomResource2> context) {
    numberOfExecutions.incrementAndGet();
    resource.setStatus(new SharedInformerStatus());
    resource.getStatus().setReconciledBy(getClass().getSimpleName());
    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
