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
package io.javaoperatorsdk.operator.baseapi.expectation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.expectation.ExpectationReconciler.DEPLOYMENT_READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ExpectationIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new ExpectationReconciler()).build();

  @Test
  void testExpectation() {
    extension.getReconcilerOfType(ExpectationReconciler.class).setTimeout(30000L);
    var res = testResource();
    extension.create(res);

    await()
        .untilAsserted(
            () -> {
              var actual = extension.get(ExpectationCustomResource.class, TEST_1);
              assertThat(actual.getStatus()).isNotNull();
              assertThat(actual.getStatus().getMessage()).isEqualTo(DEPLOYMENT_READY);
            });
  }

  @Test
  void expectationTimeouts() {
    extension.getReconcilerOfType(ExpectationReconciler.class).setTimeout(300L);
    var res = testResource();
    extension.create(res);

    await()
        .untilAsserted(
            () -> {
              var actual = extension.get(ExpectationCustomResource.class, TEST_1);
              assertThat(actual.getStatus()).isNotNull();
              assertThat(actual.getStatus().getMessage()).isEqualTo(DEPLOYMENT_READY);
            });
  }

  private ExpectationCustomResource testResource() {
    var res = new ExpectationCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_1).build());
    return res;
  }
}
