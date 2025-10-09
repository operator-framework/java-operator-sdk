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
package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;
import io.javaoperatorsdk.operator.sample.dependentresource.*;

import static io.javaoperatorsdk.operator.sample.Utils.*;

/** Shows how to implement a reconciler with managed dependent resources. */
@Workflow(
    dependents = {
      @Dependent(type = ConfigMapDependentResource.class),
      @Dependent(type = DeploymentDependentResource.class),
      @Dependent(type = ServiceDependentResource.class),
      @Dependent(
          type = IngressDependentResource.class,
          reconcilePrecondition = ExposedIngressCondition.class)
    })
public class WebPageManagedDependentsReconciler implements Reconciler<WebPage>, Cleaner<WebPage> {

  public static final String SELECTOR = "managed";

  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(
      WebPage resource, Context<WebPage> context, Exception e) {
    return handleError(resource, e);
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    simulateErrorIfRequested(webPage);

    final var name =
        context.getSecondaryResource(ConfigMap.class).orElseThrow().getMetadata().getName();
    return UpdateControl.patchStatus(createWebPageForStatusUpdate(webPage, name));
  }

  @Override
  public DeleteControl cleanup(WebPage resource, Context<WebPage> context) {
    return DeleteControl.defaultDelete();
  }
}
