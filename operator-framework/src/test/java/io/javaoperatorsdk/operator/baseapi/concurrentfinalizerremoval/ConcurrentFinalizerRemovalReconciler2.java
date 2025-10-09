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
package io.javaoperatorsdk.operator.baseapi.concurrentfinalizerremoval;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class ConcurrentFinalizerRemovalReconciler2
    implements Reconciler<ConcurrentFinalizerRemovalCustomResource>,
        Cleaner<ConcurrentFinalizerRemovalCustomResource> {

  @Override
  public UpdateControl<ConcurrentFinalizerRemovalCustomResource> reconcile(
      ConcurrentFinalizerRemovalCustomResource resource,
      Context<ConcurrentFinalizerRemovalCustomResource> context) {
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      ConcurrentFinalizerRemovalCustomResource resource,
      Context<ConcurrentFinalizerRemovalCustomResource> context)
      throws Exception {
    return DeleteControl.defaultDelete();
  }
}
