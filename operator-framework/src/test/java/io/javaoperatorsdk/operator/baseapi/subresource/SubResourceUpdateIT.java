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
package io.javaoperatorsdk.operator.baseapi.subresource;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.support.TestUtils;

import static io.javaoperatorsdk.operator.baseapi.subresource.SubResourceTestCustomResourceStatus.State.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Status Subresource Updates",
    description =
        """
        Demonstrates how to properly update the status subresource of custom resources. The test \
        verifies that status updates are handled correctly without triggering unnecessary \
        reconciliations, and that concurrent spec and status updates are managed properly with \
        optimistic locking and retry mechanisms.
        """)
class SubResourceUpdateIT {

  public static final int WAIT_AFTER_EXECUTION = 500;
  public static final int EVENT_RECEIVE_WAIT = 200;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(SubResourceTestCustomReconciler.class)
          .build();

  @Test
  void updatesSubResourceStatus() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    operator.create(resource);

    awaitStatusUpdated(resource.getMetadata().getName());
    // wait for sure, there are no more events
    waitXms(WAIT_AFTER_EXECUTION);
    // there is no event on status update processed
    assertThat(TestUtils.getNumberOfExecutions(operator)).isEqualTo(1);
  }

  @Test
  void updatesSubResourceStatusNoFinalizer() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    resource.getMetadata().setFinalizers(Collections.emptyList());

    operator.create(resource);

    awaitStatusUpdated(resource.getMetadata().getName());
    // wait for sure, there are no more events
    waitXms(WAIT_AFTER_EXECUTION);
    // there is no event on status update processed
    assertThat(TestUtils.getNumberOfExecutions(operator)).isEqualTo(1);
  }

  /** Note that we check on controller impl if there is finalizer on execution. */
  @Test
  void ifNoFinalizerPresentFirstAddsTheFinalizerThenExecutesControllerAgain() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    resource.getMetadata().getFinalizers().clear();
    operator.create(resource);

    awaitStatusUpdated(resource.getMetadata().getName());
    // wait for sure, there are no more events
    waitXms(WAIT_AFTER_EXECUTION);
    // there is no event on status update processed
    assertThat(TestUtils.getNumberOfExecutions(operator)).isEqualTo(1);
  }

  /**
   * The update status actually does optimistic locking in the background but fabric8 client retries
   * it with an up-to-date resource version.
   */
  @Test
  void updateCustomResourceAfterSubResourceChange() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    resource = operator.create(resource);

    // waits for the resource to start processing
    waitXms(EVENT_RECEIVE_WAIT);
    resource.getSpec().setValue("new value");
    operator.resources(SubResourceTestCustomResource.class).resource(resource).createOrReplace();

    awaitStatusUpdated(resource.getMetadata().getName());

    // wait for sure, there are no more events
    waitXms(WAIT_AFTER_EXECUTION);
    // note that both is valid, since after the update of the status the event receive lags,
    // that will result in a third execution
    assertThat(TestUtils.getNumberOfExecutions(operator)).isBetween(2, 3);
  }

  void awaitStatusUpdated(String name) {
    await("cr status updated")
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              SubResourceTestCustomResource cr =
                  operator.get(SubResourceTestCustomResource.class, name);
              assertThat(cr).isNotNull();
              assertThat(cr.getStatus()).isNotNull();
              assertThat(cr.getStatus().getState()).isEqualTo(SUCCESS);
            });
  }

  public SubResourceTestCustomResource createTestCustomResource(String id) {
    SubResourceTestCustomResource resource = new SubResourceTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName("subresource-" + id).build());
    resource.setKind("SubresourceSample");
    resource.setSpec(new SubResourceTestCustomResourceSpec());
    resource.getSpec().setValue(id);
    return resource;
  }

  public static void waitXms(int x) {
    try {
      Thread.sleep(x);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }
}
