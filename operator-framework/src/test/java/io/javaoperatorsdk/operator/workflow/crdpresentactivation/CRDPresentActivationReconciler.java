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
package io.javaoperatorsdk.operator.workflow.crdpresentactivation;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.CRDPresentActivationCondition;

@Workflow(
    dependents = {
      @Dependent(
          type = CRDPresentActivationDependent.class,
          activationCondition = CRDPresentActivationCondition.class),
    })
// to trigger reconciliation with metadata change
@ControllerConfiguration(generationAwareEventProcessing = false)
public class CRDPresentActivationReconciler
    implements Reconciler<CRDPresentActivationCustomResource>,
        Cleaner<CRDPresentActivationCustomResource> {

  @Override
  public UpdateControl<CRDPresentActivationCustomResource> reconcile(
      CRDPresentActivationCustomResource resource,
      Context<CRDPresentActivationCustomResource> context) {

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      CRDPresentActivationCustomResource resource,
      Context<CRDPresentActivationCustomResource> context) {
    return DeleteControl.defaultDelete();
  }
}
