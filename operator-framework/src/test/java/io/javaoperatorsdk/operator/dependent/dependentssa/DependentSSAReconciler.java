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
package io.javaoperatorsdk.operator.dependent.dependentssa;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class DependentSSAReconciler
    implements Reconciler<DependentSSACustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final SSAConfigMapDependent ssaConfigMapDependent = new SSAConfigMapDependent();
  private final boolean useSSA;

  public DependentSSAReconciler() {
    this(true);
  }

  public DependentSSAReconciler(boolean useSSA) {
    ssaConfigMapDependent.configureWith(
        new KubernetesDependentResourceConfigBuilder<ConfigMap>().withUseSSA(useSSA).build());
    this.useSSA = useSSA;
  }

  public boolean isUseSSA() {
    return useSSA;
  }

  public SSAConfigMapDependent getSsaConfigMapDependent() {
    return ssaConfigMapDependent;
  }

  @Override
  public UpdateControl<DependentSSACustomResource> reconcile(
      DependentSSACustomResource resource, Context<DependentSSACustomResource> context) {

    ssaConfigMapDependent.reconcile(resource, context);
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource<?, DependentSSACustomResource>> prepareEventSources(
      EventSourceContext<DependentSSACustomResource> context) {
    return EventSourceUtils.dependentEventSources(context, ssaConfigMapDependent);
  }
}
