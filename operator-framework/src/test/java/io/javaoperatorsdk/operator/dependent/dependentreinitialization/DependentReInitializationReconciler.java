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
package io.javaoperatorsdk.operator.dependent.dependentreinitialization;

import java.util.List;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class DependentReInitializationReconciler
    implements Reconciler<DependentReInitializationCustomResource> {

  private final ConfigMapDependentResource configMapDependentResource;

  public DependentReInitializationReconciler(ConfigMapDependentResource dependentResource) {
    this.configMapDependentResource = dependentResource;
  }

  @Override
  public UpdateControl<DependentReInitializationCustomResource> reconcile(
      DependentReInitializationCustomResource resource,
      Context<DependentReInitializationCustomResource> context)
      throws Exception {
    configMapDependentResource.reconcile(resource, context);
    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, DependentReInitializationCustomResource>> prepareEventSources(
      EventSourceContext<DependentReInitializationCustomResource> context) {
    return EventSourceUtils.dependentEventSources(context, configMapDependentResource);
  }
}
