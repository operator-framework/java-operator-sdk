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
package io.javaoperatorsdk.operator.baseapi.deployment;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(
    informer = @Informer(labelSelector = "test=KubernetesResourceStatusUpdateIT"))
public class DeploymentReconciler implements Reconciler<Deployment>, TestExecutionInfoProvider {

  public static final String STATUS_MESSAGE = "Reconciled by DeploymentReconciler";

  private static final Logger log = LoggerFactory.getLogger(DeploymentReconciler.class);
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<Deployment> reconcile(Deployment resource, Context<Deployment> context) {

    log.info("Reconcile deployment: {}", resource);
    numberOfExecutions.incrementAndGet();
    if (resource.getStatus() == null) {
      resource.setStatus(new DeploymentStatus());
    }
    if (resource.getStatus().getConditions() == null) {
      resource.getStatus().setConditions(new ArrayList<>());
    }
    var conditions = resource.getStatus().getConditions();
    var condition =
        conditions.stream().filter(c -> c.getMessage().equals(STATUS_MESSAGE)).findFirst();
    if (condition.isEmpty()) {
      conditions.add(
          new DeploymentCondition(
              null, null, STATUS_MESSAGE, null, "unknown", "DeploymentReconciler"));
      return UpdateControl.patchStatus(resource);
    } else {
      return UpdateControl.noUpdate();
    }
  }

  @Override
  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
