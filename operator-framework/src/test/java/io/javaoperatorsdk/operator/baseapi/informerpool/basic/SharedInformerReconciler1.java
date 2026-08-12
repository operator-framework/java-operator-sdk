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
 * Watches {@link ConfigMap} as a secondary resource. Together with {@link
 * SharedInformerReconciler2}, which watches the same secondary resource type with an identical
 * configuration, this is used to verify that both controllers share a single underlying informer
 * from the informer pool.
 */
@ControllerConfiguration
public class SharedInformerReconciler1 implements Reconciler<SharedInformerCustomResource1> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public List<EventSource<?, SharedInformerCustomResource1>> prepareEventSources(
      EventSourceContext<SharedInformerCustomResource1> context) {
    var config =
        InformerEventSourceConfiguration.from(ConfigMap.class, SharedInformerCustomResource1.class)
            .build();
    return List.of(new InformerEventSource<>(config));
  }

  @Override
  public UpdateControl<SharedInformerCustomResource1> reconcile(
      SharedInformerCustomResource1 resource, Context<SharedInformerCustomResource1> context) {
    numberOfExecutions.incrementAndGet();
    resource.setStatus(new SharedInformerStatus());
    resource.getStatus().setReconciledBy(getClass().getSimpleName());
    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
