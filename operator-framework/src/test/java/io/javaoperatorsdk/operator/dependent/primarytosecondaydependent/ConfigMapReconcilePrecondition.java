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
package io.javaoperatorsdk.operator.dependent.primarytosecondaydependent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ConfigMapReconcilePrecondition
    implements Condition<ConfigMap, PrimaryToSecondaryDependentCustomResource> {

  public static final String DO_NOT_RECONCILE = "doNotReconcile";

  @Override
  public boolean isMet(
      DependentResource<ConfigMap, PrimaryToSecondaryDependentCustomResource> dependentResource,
      PrimaryToSecondaryDependentCustomResource primary,
      Context<PrimaryToSecondaryDependentCustomResource> context) {
    return dependentResource
        .getSecondaryResource(primary, context)
        .map(
            cm -> {
              var data = cm.getData().get(PrimaryToSecondaryDependentReconciler.DATA_KEY);
              return data != null && !data.equals(DO_NOT_RECONCILE);
            })
        .orElse(false);
  }
}
