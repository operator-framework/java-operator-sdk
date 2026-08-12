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
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

/**
 * The reconciled resource is itself a generic type, so the resolved resource type is a
 * parameterized {@code DeclaredType}. Only its erasure can be written to the mapping resource,
 * because that is the only form {@code ClassMappingProvider} is able to load at runtime.
 */
@ControllerConfiguration
public class GenericResourceReconciler implements
    Reconciler<GenericResourceReconciler.MyGenericCustomResource<String>> {

  public static class MyGenericCustomResource<S> extends CustomResource<S, Void> {
  }

  @Override
  public UpdateControl<MyGenericCustomResource<String>> reconcile(
      MyGenericCustomResource<String> customResource,
      Context<MyGenericCustomResource<String>> context) {
    return UpdateControl.noUpdate();
  }
}
