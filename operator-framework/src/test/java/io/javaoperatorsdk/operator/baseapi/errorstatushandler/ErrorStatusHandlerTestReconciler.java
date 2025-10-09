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
package io.javaoperatorsdk.operator.baseapi.errorstatushandler;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class ErrorStatusHandlerTestReconciler
    implements Reconciler<ErrorStatusHandlerTestCustomResource>, TestExecutionInfoProvider {

  private static final Logger log = LoggerFactory.getLogger(ErrorStatusHandlerTestReconciler.class);
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  public static final String ERROR_STATUS_MESSAGE = "Error Retries Exceeded";

  @Override
  public UpdateControl<ErrorStatusHandlerTestCustomResource> reconcile(
      ErrorStatusHandlerTestCustomResource resource,
      Context<ErrorStatusHandlerTestCustomResource> context) {
    var number = numberOfExecutions.addAndGet(1);
    var retryAttempt = -1;
    if (context.getRetryInfo().isPresent()) {
      retryAttempt = context.getRetryInfo().get().getAttemptCount();
    }
    log.info(
        "Number of execution: {}  retry attempt: {} , resource: {}",
        number,
        retryAttempt,
        resource);
    throw new IllegalStateException();
  }

  private void ensureStatusExists(ErrorStatusHandlerTestCustomResource resource) {
    ErrorStatusHandlerTestCustomResourceStatus status = resource.getStatus();
    if (status == null) {
      status = new ErrorStatusHandlerTestCustomResourceStatus();
      resource.setStatus(status);
    }
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public ErrorStatusUpdateControl<ErrorStatusHandlerTestCustomResource> updateErrorStatus(
      ErrorStatusHandlerTestCustomResource resource,
      Context<ErrorStatusHandlerTestCustomResource> context,
      Exception e) {
    log.info("Setting status.");
    ensureStatusExists(resource);
    resource
        .getStatus()
        .getMessages()
        .add(ERROR_STATUS_MESSAGE + context.getRetryInfo().orElseThrow().getAttemptCount());
    return ErrorStatusUpdateControl.patchStatus(resource);
  }
}
