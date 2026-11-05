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
package io.javaoperatorsdk.operator.baseapi.virtualthreads;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

/**
 * Blocks for a while during reconciliation, recording on what kind of thread it ran and how many
 * reconciliations were in flight at the same time.
 */
@ControllerConfiguration
public class VirtualThreadsTestReconciler
    implements Reconciler<VirtualThreadsCustomResource>, TestExecutionInfoProvider {

  public static final Duration RECONCILIATION_DURATION = Duration.ofMillis(300);

  private final AtomicInteger numberOfExecutions = new AtomicInteger();
  private final AtomicInteger runningReconciliations = new AtomicInteger();
  private final AtomicInteger maxConcurrentReconciliations = new AtomicInteger();
  private final AtomicBoolean allExecutionsOnVirtualThreads = new AtomicBoolean(true);

  @Override
  public UpdateControl<VirtualThreadsCustomResource> reconcile(
      VirtualThreadsCustomResource resource, Context<VirtualThreadsCustomResource> context)
      throws InterruptedException {
    if (!onVirtualThread()) {
      allExecutionsOnVirtualThreads.set(false);
    }
    maxConcurrentReconciliations.accumulateAndGet(
        runningReconciliations.incrementAndGet(), Math::max);
    try {
      Thread.sleep(RECONCILIATION_DURATION.toMillis());
    } finally {
      runningReconciliations.decrementAndGet();
      numberOfExecutions.incrementAndGet();
    }
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public int getMaxConcurrentReconciliations() {
    return maxConcurrentReconciliations.get();
  }

  public boolean allExecutionsOnVirtualThreads() {
    return allExecutionsOnVirtualThreads.get();
  }

  /**
   * {@code Thread.isVirtual} only exists as of Java 21 while the tests are compiled for Java 17,
   * hence the reflective call.
   */
  static boolean onVirtualThread() {
    try {
      return (Boolean) Thread.class.getMethod("isVirtual").invoke(Thread.currentThread());
    } catch (ReflectiveOperationException e) {
      return false;
    }
  }
}
