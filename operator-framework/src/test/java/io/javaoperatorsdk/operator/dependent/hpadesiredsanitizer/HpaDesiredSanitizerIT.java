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
package io.javaoperatorsdk.operator.dependent.hpadesiredsanitizer;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer.StatefulSetDesiredSanitizerCustomResource;
import io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer.StatefulSetDesiredSanitizerSpec;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Matching HorizontalPodAutoscaler target quantities with SSA",
    description =
        """
        Shows that the SSA based matcher treats numerically equal HorizontalPodAutoscaler \
        target quantities as equal, although the API server stores them in canonical form \
        (for example 1000 as 1k), so the HPA is not updated on every reconciliation.
        """)
public class HpaDesiredSanitizerIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new HpaDesiredSanitizerReconciler())
          .build();

  @Test
  void testSSAMatcher() {
    var resource = extension.create(testResource());

    await()
        .pollDelay(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              var hpa = extension.get(HorizontalPodAutoscaler.class, TEST_1);
              assertThat(hpa).isNotNull();
              assertThat(
                      hpa.getSpec()
                          .getMetrics()
                          .get(0)
                          .getExternal()
                          .getTarget()
                          .getValue()
                          .toString())
                  .isEqualTo("1k");
            });
    // make sure the changed primary is reconciled and matched at least once
    resource.getSpec().setValue("changed value");
    extension.replace(resource);

    await()
        .untilAsserted(
            () ->
                assertThat(HpaDesiredSanitizerDependentResource.matchedPrimaryValues)
                    .contains("changed value"));
    assertThat(HpaDesiredSanitizerDependentResource.nonMatchedAtLeastOnce).isFalse();
  }

  StatefulSetDesiredSanitizerCustomResource testResource() {
    var res = new StatefulSetDesiredSanitizerCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_1).build());
    res.setSpec(new StatefulSetDesiredSanitizerSpec());
    res.getSpec().setValue("initial value");

    return res;
  }
}
