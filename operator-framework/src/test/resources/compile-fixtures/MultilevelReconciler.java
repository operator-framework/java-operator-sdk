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
package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class MultilevelReconciler extends
    MultilevelAbstractReconciler<String, MultilevelReconciler.MyCustomResource> {

  public static class MyCustomResource extends CustomResource<Void,Void> {

  }

  public UpdateControl<MultilevelReconciler.MyCustomResource> reconcile(
          MultilevelReconciler.MyCustomResource customResource,
      Context context) {
    return UpdateControl.patchResource(null);
  }

  public DeleteControl cleanup(MultilevelReconciler.MyCustomResource customResource,
      Context context) {
    return DeleteControl.defaultDelete();
  }

}
