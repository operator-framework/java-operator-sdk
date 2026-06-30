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
package io.javaoperatorsdk.operator.baseapi.statuseventfiltering;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class StatusEventSecondaryReconciler implements Reconciler<StatusEventSecondaryResource> {

  private static final Logger log = LoggerFactory.getLogger(StatusEventSecondaryReconciler.class);

  @Override
  public UpdateControl<StatusEventSecondaryResource> reconcile(
      StatusEventSecondaryResource resource, Context<StatusEventSecondaryResource> context) {

    var generation = resource.getMetadata().getGeneration();
    var observedGeneration = resource.getStatus().getObservedGeneration();

    if (!Objects.equals(generation, observedGeneration)) {
      log.info(
          "Patching secondary '{}' status: obsGen {} -> {}",
          resource.getMetadata().getName(),
          observedGeneration,
          generation);
      resource.getStatus().setObservedGeneration(generation);
      return UpdateControl.patchStatus(resource);
    }

    return UpdateControl.noUpdate();
  }
}
