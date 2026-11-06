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
package io.javaoperatorsdk.operator.dependent.hpadesiredsanitizer;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer.StatefulSetDesiredSanitizerCustomResource;

@Workflow(dependents = {@Dependent(type = HpaDesiredSanitizerDependentResource.class)})
@ControllerConfiguration
public class HpaDesiredSanitizerReconciler
    implements Reconciler<StatefulSetDesiredSanitizerCustomResource> {

  @Override
  public UpdateControl<StatefulSetDesiredSanitizerCustomResource> reconcile(
      StatefulSetDesiredSanitizerCustomResource resource,
      Context<StatefulSetDesiredSanitizerCustomResource> context)
      throws Exception {
    return UpdateControl.noUpdate();
  }
}
