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
package io.javaoperatorsdk.operator.dependent.ssalegacymatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Using Legacy Resource Matcher with SSA",
    description =
        """
        Demonstrates using the legacy resource matcher with Server-Side Apply (SSA). The legacy \
        matcher provides backward compatibility for matching logic while using SSA for updates, \
        ensuring that resource comparisons work correctly even when migrating from traditional \
        update methods to SSA.
        """)
public class SSAWithLegacyMatcherIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new SSALegacyMatcherReconciler())
          .build();

  @Test
  void matchesDependentWithLegacyMatcher() {
    var resource = extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var service = extension.get(Service.class, TEST_RESOURCE_NAME);
              assertThat(service).isNotNull();
              assertThat(ServiceDependentResource.createUpdateCount.get()).isEqualTo(1);
            });

    resource.getSpec().setValue("other_value");

    await()
        .untilAsserted(
            () -> {
              assertThat(ServiceDependentResource.createUpdateCount.get()).isEqualTo(1);
            });
  }

  SSALegacyMatcherCustomResource testResource() {
    SSALegacyMatcherCustomResource res = new SSALegacyMatcherCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    res.setSpec(new SSALegacyMatcherSpec());
    res.getSpec().setValue("initial-value");
    return res;
  }
}
