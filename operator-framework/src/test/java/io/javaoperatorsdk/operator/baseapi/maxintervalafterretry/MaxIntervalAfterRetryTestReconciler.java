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
package io.javaoperatorsdk.operator.baseapi.maxintervalafterretry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@GradualRetry(maxAttempts = 1, initialInterval = 100)
@ControllerConfiguration(
    maxReconciliationInterval =
        @MaxReconciliationInterval(interval = 50, timeUnit = TimeUnit.MILLISECONDS))
public class MaxIntervalAfterRetryTestReconciler
    implements Reconciler<MaxIntervalAfterRetryTestCustomResource>, TestExecutionInfoProvider {

  private static final Logger log =
      LoggerFactory.getLogger(MaxIntervalAfterRetryTestReconciler.class);

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<MaxIntervalAfterRetryTestCustomResource> reconcile(
      MaxIntervalAfterRetryTestCustomResource resource,
      Context<MaxIntervalAfterRetryTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    log.info("Executed, number: {}", numberOfExecutions.get());
    throw new RuntimeException();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
