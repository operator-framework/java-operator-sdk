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
package io.javaoperatorsdk.operator.dependent.desiredstateaspect;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

@Workflow(
    dependents = @Dependent(type = DesiredStateAspectReconciler.ConfigMapDependentResource.class))
@ControllerConfiguration
public class DesiredStateAspectReconciler implements Reconciler<DesiredStateAspectCustomResource> {

  @Override
  public UpdateControl<DesiredStateAspectCustomResource> reconcile(
      DesiredStateAspectCustomResource resource,
      Context<DesiredStateAspectCustomResource> context) {
    return UpdateControl.noUpdate();
  }

  public static class ConfigMapDependentResource
      extends CRUDKubernetesDependentResource<ConfigMap, DesiredStateAspectCustomResource> {

    @Override
    protected ConfigMap desired(
        DesiredStateAspectCustomResource primary,
        Context<DesiredStateAspectCustomResource> context) {
      ConfigMap configMap = new ConfigMap();
      configMap.setMetadata(
          new ObjectMetaBuilder()
              .withName(primary.getMetadata().getName())
              .withNamespace(primary.getMetadata().getNamespace())
              .build());
      configMap.setData(Map.of("data", primary.getMetadata().getName()));
      return configMap;
    }
  }
}
