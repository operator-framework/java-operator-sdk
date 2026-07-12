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

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AddFinalizerNoSSAIT {

  public static final String TEST_RESOURCE_NAME = "add-finalizer-no-ssa-test1";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(o -> o.withUseSSAToPatchPrimaryResource(false))
          .withReconciler(new AddFinalizerNoSSAReconciler())
          .build();

  @Test
  void addsFinalizerWithoutSSAAndRemovesItOnCleanup() {
    var reconciler = operator.getReconcilerOfType(AddFinalizerNoSSAReconciler.class);

    var testResource = createTestResource();
    operator.create(testResource);

    await("finalizer added")
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var actual = operator.get(AddFinalizerNoSSACustomResource.class, TEST_RESOURCE_NAME);
              assertThat(actual).isNotNull();
              assertThat(actual.getMetadata().getFinalizers()).hasSize(1);
            });

    operator.delete(testResource);

    await("resource deleted after finalizer removal")
        .atMost(5, TimeUnit.SECONDS)
        .until(
            () -> operator.get(AddFinalizerNoSSACustomResource.class, TEST_RESOURCE_NAME) == null);

    assertThat(reconciler.getNumberOfExecutions()).isEqualTo(1);
    assertThat(reconciler.getNumberOfCleanupExecutions()).isEqualTo(1);
  }

  private AddFinalizerNoSSACustomResource createTestResource() {
    AddFinalizerNoSSACustomResource cr = new AddFinalizerNoSSACustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(TEST_RESOURCE_NAME);
    return cr;
  }
}
