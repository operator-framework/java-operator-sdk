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
package io.javaoperatorsdk.operator.baseapi.cleanupconflict;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration
public class CleanupConflictReconciler
    implements Reconciler<CleanupConflictCustomResource>, Cleaner<CleanupConflictCustomResource> {

  public static final long WAIT_TIME = 500L;
  private final AtomicInteger numberOfCleanupExecutions = new AtomicInteger(0);
  private final AtomicInteger numberReconcileExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<CleanupConflictCustomResource> reconcile(
      CleanupConflictCustomResource resource, Context<CleanupConflictCustomResource> context) {
    numberReconcileExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      CleanupConflictCustomResource resource, Context<CleanupConflictCustomResource> context) {
    numberOfCleanupExecutions.addAndGet(1);
    try {
      Thread.sleep(WAIT_TIME);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return DeleteControl.defaultDelete();
  }

  public int getNumberOfCleanupExecutions() {
    return numberOfCleanupExecutions.intValue();
  }

  public int getNumberReconcileExecutions() {
    return numberReconcileExecutions.intValue();
  }
}
