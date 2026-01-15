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
package io.javaoperatorsdk.operator.baseapi.triggerallevent.finalizerhandling;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ReconcileUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(triggerReconcilerOnAllEvents = true)
public class SelectiveFinalizerHandlingReconciler
    implements Reconciler<SelectiveFinalizerHandlingReconcilerCustomResource> {

  public static final String FINALIZER = "finalizer.test/finalizer";

  @Override
  public UpdateControl<SelectiveFinalizerHandlingReconcilerCustomResource> reconcile(
      SelectiveFinalizerHandlingReconcilerCustomResource resource,
      Context<SelectiveFinalizerHandlingReconcilerCustomResource> context) {

    if (context.isPrimaryResourceDeleted()) {
      return UpdateControl.noUpdate();
    }

    if (resource.getSpec().getUseFinalizer()) {
      ReconcileUtils.addFinalizer(context, FINALIZER);
    }

    if (resource.isMarkedForDeletion()) {
      ReconcileUtils.removeFinalizer(context, FINALIZER);
    }

    return UpdateControl.noUpdate();
  }
}
