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
package io.javaoperatorsdk.operator.dependent.detectapiversionchange;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Detects API version changes of a dependent resource",
    description =
        """
        Shows how a dependent resource configured with `detectApiVersionChange` records the API \
        version it applies in a marker annotation, and how a stale marker (for example left \
        behind by an older CRD/operator version) triggers exactly one update to bring the marker \
        back up to date, without causing further reconciliation loops.
        """)
class DetectApiVersionChangeIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String STALE_API_VERSION = "stale.example.com/v1alpha1";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new DetectApiVersionChangeReconciler())
          .build();

  @Test
  void marksAndFixesStaleApiVersionMarker() {
    operator.create(testResource());

    // the marker annotation is set to the current API version on initial creation
    await()
        .untilAsserted(
            () -> {
              var configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(configMap).isNotNull();
              assertThat(configMap.getMetadata().getAnnotations())
                  .containsEntry(
                      KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY, "v1");
            });

    // creation does not count as an update
    await()
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(() -> assertThat(ConfigMapDependentResource.updateCount.get()).isZero());

    // simulate a marker left behind by an older operator/CRD version
    var configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
    configMap
        .getMetadata()
        .getAnnotations()
        .put(
            KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY, STALE_API_VERSION);
    operator.update(configMap);

    // the stale marker is detected and fixed with a single update
    await()
        .untilAsserted(
            () -> {
              var updated = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(updated.getMetadata().getAnnotations())
                  .containsEntry(
                      KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY, "v1");
              assertThat(ConfigMapDependentResource.updateCount.get()).isEqualTo(1);
            });

    // no further updates are triggered once the marker is up-to-date again
    await()
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(() -> assertThat(ConfigMapDependentResource.updateCount.get()).isEqualTo(1));
  }

  DetectApiVersionChangeCustomResource testResource() {
    var res = new DetectApiVersionChangeCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}
