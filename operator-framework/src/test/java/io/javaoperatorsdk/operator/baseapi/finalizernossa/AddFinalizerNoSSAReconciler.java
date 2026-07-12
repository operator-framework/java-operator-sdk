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
package io.javaoperatorsdk.operator.baseapi.finalizernossa;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class AddFinalizerNoSSAReconciler
    implements Reconciler<AddFinalizerNoSSACustomResource>,
        Cleaner<AddFinalizerNoSSACustomResource>,
        TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final AtomicInteger numberOfCleanupExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<AddFinalizerNoSSACustomResource> reconcile(
      AddFinalizerNoSSACustomResource resource, Context<AddFinalizerNoSSACustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      AddFinalizerNoSSACustomResource resource, Context<AddFinalizerNoSSACustomResource> context) {
    numberOfCleanupExecutions.addAndGet(1);
    return DeleteControl.defaultDelete();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public int getNumberOfCleanupExecutions() {
    return numberOfCleanupExecutions.get();
  }
}
