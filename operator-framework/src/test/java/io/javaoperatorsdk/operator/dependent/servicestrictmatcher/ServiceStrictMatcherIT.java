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
package io.javaoperatorsdk.operator.dependent.servicestrictmatcher;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ServiceStrictMatcherIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ServiceStrictMatcherTestReconciler())
          .build();

  @Test
  void testTheMatchingDoesNoTTriggersFurtherUpdates() {
    var resource = operator.create(testResource());

    await()
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(ServiceStrictMatcherTestReconciler.class)
                          .getNumberOfExecutions())
                  .isEqualTo(1);
            });

    // make an update to spec to reconcile again
    resource.getSpec().setValue(2);
    operator.replace(resource);

    await()
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(ServiceStrictMatcherTestReconciler.class)
                          .getNumberOfExecutions())
                  .isEqualTo(2);
              assertThat(ServiceDependentResource.updated.get()).isZero();
            });
  }

  ServiceStrictMatcherTestCustomResource testResource() {
    var res = new ServiceStrictMatcherTestCustomResource();
    res.setSpec(new ServiceStrictMatcherSpec());
    res.getSpec().setValue(1);
    res.setMetadata(new ObjectMetaBuilder().withName("test1").build());
    return res;
  }
}
