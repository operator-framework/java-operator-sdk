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
package io.javaoperatorsdk.operator.baseapi.patchresourceandstatusnossa;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class PatchResourceAndStatusNoSSAReconciler
    implements Reconciler<PatchResourceAndStatusNoSSACustomResource>, TestExecutionInfoProvider {

  private static final Logger log =
      LoggerFactory.getLogger(PatchResourceAndStatusNoSSAReconciler.class);
  public static final String TEST_ANNOTATION = "TestAnnotation";
  public static final String TEST_ANNOTATION_VALUE = "TestAnnotationValue";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private volatile boolean removeAnnotation = false;

  @Override
  public UpdateControl<PatchResourceAndStatusNoSSACustomResource> reconcile(
      PatchResourceAndStatusNoSSACustomResource resource,
      Context<PatchResourceAndStatusNoSSACustomResource> context) {
    numberOfExecutions.addAndGet(1);

    log.info("Value: {}", resource.getSpec().getValue());

    if (removeAnnotation) {
      resource.getMetadata().getAnnotations().remove(TEST_ANNOTATION);
    } else {
      resource.getMetadata().setAnnotations(new HashMap<>());
      resource.getMetadata().getAnnotations().put(TEST_ANNOTATION, TEST_ANNOTATION_VALUE);
    }
    ensureStatusExists(resource);
    resource.getStatus().setState(PatchResourceAndStatusNoSSAStatus.State.SUCCESS);

    return UpdateControl.patchResourceAndStatus(resource);
  }

  private void ensureStatusExists(PatchResourceAndStatusNoSSACustomResource resource) {
    PatchResourceAndStatusNoSSAStatus status = resource.getStatus();
    if (status == null) {
      status = new PatchResourceAndStatusNoSSAStatus();
      resource.setStatus(status);
    }
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public void setRemoveAnnotation(boolean removeAnnotation) {
    this.removeAnnotation = removeAnnotation;
  }
}
