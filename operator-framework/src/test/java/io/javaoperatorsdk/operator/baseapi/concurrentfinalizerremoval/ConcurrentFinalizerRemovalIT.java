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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Concurrent Finalizer Removal by Multiple Reconcilers",
    description =
        """
        Demonstrates safe concurrent finalizer removal when multiple reconcilers manage the \
        same resource with different finalizers. Tests that finalizers can be removed \
        concurrently without conflicts or race conditions, ensuring proper cleanup even when \
        multiple controllers are involved.
        """)
class ConcurrentFinalizerRemovalIT {

  private static final Logger log = LoggerFactory.getLogger(ConcurrentFinalizerRemovalIT.class);
  public static final String TEST_RESOURCE_NAME = "test";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          // should work without a retry, thus not retry the whole reconciliation but to retry
          // finalizer removal only.
          .withReconciler(
              new ConcurrentFinalizerRemovalReconciler1(),
              o ->
                  o.withRetry(GenericRetry.noRetry()).withFinalizer("reconciler1.sample/finalizer"))
          .withReconciler(
              new ConcurrentFinalizerRemovalReconciler2(),
              o ->
                  o.withRetry(GenericRetry.noRetry()).withFinalizer("reconciler2.sample/finalizer"))
          .build();

  @Test
  void concurrentFinalizerRemoval() {
    for (int i = 0; i < 10; i++) {
      var resource = extension.create(createResource());
      await()
          .untilAsserted(
              () -> {
                var res =
                    extension.get(
                        ConcurrentFinalizerRemovalCustomResource.class, TEST_RESOURCE_NAME);
                assertThat(res.getMetadata().getFinalizers()).hasSize(2);
              });
      resource.getMetadata().setResourceVersion(null);
      extension.delete(resource);

      await()
          .untilAsserted(
              () -> {
                var res =
                    extension.get(
                        ConcurrentFinalizerRemovalCustomResource.class, TEST_RESOURCE_NAME);
                assertThat(res).isNull();
              });
    }
  }

  public ConcurrentFinalizerRemovalCustomResource createResource() {
    ConcurrentFinalizerRemovalCustomResource res = new ConcurrentFinalizerRemovalCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    res.setSpec(new ConcurrentFinalizerRemovalSpec());
    res.getSpec().setNumber(0);
    return res;
  }
}
