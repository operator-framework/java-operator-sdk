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
package io.javaoperatorsdk.operator.baseapi.cachingfilteringupdate;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CachingFilteringUpdateIT {

  public static final int RESOURCE_NUMBER = 250;
  CachingFilteringUpdateReconciler reconciler = new CachingFilteringUpdateReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler).build();

  @Test
  void testResourceAccessAfterUpdate() {
    for (int i = 0; i < RESOURCE_NUMBER; i++) {
      operator.create(createCustomResource(i));
    }
    await()
        .pollDelay(Duration.ofSeconds(5))
        .atMost(Duration.ofMinutes(1))
        .until(
            () -> {
              if (reconciler.isIssueFound()) {
                // Stop waiting as soon as an issue is detected.
                return true;
              }
              // Use a single representative resource to detect that updates have completed.
              var res =
                  operator.get(
                      CachingFilteringUpdateCustomResource.class,
                      "resource" + (RESOURCE_NUMBER - 1));
              return res != null
                  && res.getStatus() != null
                  && Boolean.TRUE.equals(res.getStatus().getUpdated());
            });

    if (operator.getReconcilerOfType(CachingFilteringUpdateReconciler.class).isIssueFound()) {
      throw new IllegalStateException("Error already found.");
    }

    for (int i = 0; i < RESOURCE_NUMBER; i++) {
      var res = operator.get(CachingFilteringUpdateCustomResource.class, "resource" + i);
      assertThat(res.getStatus()).isNotNull();
      assertThat(res.getStatus().getUpdated()).isTrue();
    }
  }

  public CachingFilteringUpdateCustomResource createCustomResource(int i) {
    CachingFilteringUpdateCustomResource resource = new CachingFilteringUpdateCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("resource" + i)
            .withNamespace(operator.getNamespace())
            .build());
    return resource;
  }
}
