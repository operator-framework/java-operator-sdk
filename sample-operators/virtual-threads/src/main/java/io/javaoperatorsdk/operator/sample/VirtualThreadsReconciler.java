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
package io.javaoperatorsdk.operator.sample;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

/**
 * Simulates a reconciliation that spends most of its time waiting on a remote call. On virtual
 * threads such a wait does not hold on to a platform thread, thus an arbitrary number of resources
 * can be reconciled in parallel.
 */
public class VirtualThreadsReconciler implements Reconciler<VirtualThreadsCustomResource> {

  public static final Duration BLOCKING_CALL_DURATION = Duration.ofSeconds(1);

  private static final Logger log = LoggerFactory.getLogger(VirtualThreadsReconciler.class);

  @Override
  public UpdateControl<VirtualThreadsCustomResource> reconcile(
      VirtualThreadsCustomResource resource, Context<VirtualThreadsCustomResource> context) {
    var thread = Thread.currentThread();
    log.info(
        "Reconciling: {} on thread: {}, virtual: {}",
        resource.getMetadata().getName(),
        thread.getName(),
        thread.isVirtual());

    simulateBlockingCall();

    var response = createResponseResource(resource);
    response.getStatus().setObservedValue(resource.getSpec().getValue());
    response.getStatus().setReconciledOnVirtualThread(thread.isVirtual());

    return UpdateControl.patchStatus(response);
  }

  private VirtualThreadsCustomResource createResponseResource(
      VirtualThreadsCustomResource resource) {
    var res = new VirtualThreadsCustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());
    res.setStatus(new VirtualThreadsStatus());
    return res;
  }

  private void simulateBlockingCall() {
    try {
      Thread.sleep(BLOCKING_CALL_DURATION);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
