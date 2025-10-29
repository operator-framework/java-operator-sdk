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
package io.javaoperatorsdk.operator.workflow.manageddependentdeletecondition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.KubernetesResourceDeletedCondition;

@Workflow(
    dependents = {
      @Dependent(name = "ConfigMap", type = ConfigMapDependent.class),
      @Dependent(
          type = SecretDependent.class,
          dependsOn = "ConfigMap",
          deletePostcondition = KubernetesResourceDeletedCondition.class)
    })
@ControllerConfiguration
public class ManagedDependentDefaultDeleteConditionReconciler
    implements Reconciler<ManagedDependentDefaultDeleteConditionCustomResource> {

  private static final Logger log =
      LoggerFactory.getLogger(ManagedDependentDefaultDeleteConditionReconciler.class);

  @Override
  public UpdateControl<ManagedDependentDefaultDeleteConditionCustomResource> reconcile(
      ManagedDependentDefaultDeleteConditionCustomResource resource,
      Context<ManagedDependentDefaultDeleteConditionCustomResource> context) {

    log.debug("Reconciled: {}", resource);

    return UpdateControl.noUpdate();
  }
}
