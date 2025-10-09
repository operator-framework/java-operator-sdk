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
package io.javaoperatorsdk.operator.baseapi.patchresourcewithssa;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration
public class PatchResourceAndStatusWithSSAReconciler
    implements Reconciler<PatchResourceWithSSACustomResource>,
        Cleaner<PatchResourceWithSSACustomResource> {

  public static final String ADDED_VALUE = "Added Value";

  @Override
  public UpdateControl<PatchResourceWithSSACustomResource> reconcile(
      PatchResourceWithSSACustomResource resource,
      Context<PatchResourceWithSSACustomResource> context) {

    var res = new PatchResourceWithSSACustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());

    res.setSpec(new PatchResourceWithSSASpec());
    res.getSpec().setControllerManagedValue(ADDED_VALUE);
    res.setStatus(new PatchResourceWithSSAStatus());
    res.getStatus().setSuccessfullyReconciled(true);

    return UpdateControl.patchResourceAndStatus(res);
  }

  @Override
  public DeleteControl cleanup(
      PatchResourceWithSSACustomResource resource,
      Context<PatchResourceWithSSACustomResource> context) {
    return DeleteControl.defaultDelete();
  }
}
