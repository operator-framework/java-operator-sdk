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
package io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class StatefulSetDesiredSanitizerIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new StatefulSetDesiredSanitizerReconciler())
          .build();

  @Test
  void testSSAMatcher() {
    var resource = extension.create(testResource());

    await()
        .pollDelay(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              var statefulSet = extension.get(StatefulSet.class, TEST_1);
              assertThat(statefulSet).isNotNull();
            });
    // make sure reconciliation happens at least once more
    resource.getSpec().setValue("changed value");
    extension.replace(resource);

    await()
        .untilAsserted(
            () ->
                assertThat(StatefulSetDesiredSanitizerDependentResource.nonMatchedAtLeastOnce)
                    .isFalse());
  }

  StatefulSetDesiredSanitizerCustomResource testResource() {
    var res = new StatefulSetDesiredSanitizerCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_1).build());
    res.setSpec(new StatefulSetDesiredSanitizerSpec());
    res.getSpec().setValue("initial value");

    return res;
  }
}
