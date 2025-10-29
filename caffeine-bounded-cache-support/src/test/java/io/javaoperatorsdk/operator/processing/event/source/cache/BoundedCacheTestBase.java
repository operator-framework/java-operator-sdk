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
package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestSpec;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestStatus;

import static io.javaoperatorsdk.operator.processing.event.source.cache.sample.AbstractTestReconciler.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class BoundedCacheTestBase<
    P extends CustomResource<BoundedCacheTestSpec, BoundedCacheTestStatus>> {

  private static final Logger log = LoggerFactory.getLogger(BoundedCacheTestBase.class);

  public static final int NUMBER_OF_RESOURCE_TO_TEST = 3;
  public static final String RESOURCE_NAME_PREFIX = "test-";
  public static final String INITIAL_DATA_PREFIX = "data-";
  public static final String UPDATED_PREFIX = "updatedPrefix";

  @Test
  void reconciliationWorksWithLimitedCache() {
    createTestResources();

    assertConfigMapData(INITIAL_DATA_PREFIX);

    updateTestResources();

    assertConfigMapData(UPDATED_PREFIX);

    deleteTestResources();

    assertConfigMapsDeleted();
  }

  private void assertConfigMapsDeleted() {
    await()
        .atMost(Duration.ofSeconds(120))
        .untilAsserted(
            () ->
                IntStream.range(0, NUMBER_OF_RESOURCE_TO_TEST)
                    .forEach(
                        i -> {
                          var cm = extension().get(ConfigMap.class, RESOURCE_NAME_PREFIX + i);
                          assertThat(cm).isNull();
                        }));
  }

  private void deleteTestResources() {
    IntStream.range(0, NUMBER_OF_RESOURCE_TO_TEST)
        .forEach(
            i -> {
              var cm = extension().get(customResourceClass(), RESOURCE_NAME_PREFIX + i);
              var deleted = extension().delete(cm);
              if (!deleted) {
                log.warn("Custom resource might not be deleted: {}", cm);
              }
            });
  }

  private void updateTestResources() {
    IntStream.range(0, NUMBER_OF_RESOURCE_TO_TEST)
        .forEach(
            i -> {
              var cm = extension().get(ConfigMap.class, RESOURCE_NAME_PREFIX + i);
              cm.getData().put(DATA_KEY, UPDATED_PREFIX + i);
              extension().replace(cm);
            });
  }

  void assertConfigMapData(String dataPrefix) {
    await()
        .untilAsserted(
            () ->
                IntStream.range(0, NUMBER_OF_RESOURCE_TO_TEST)
                    .forEach(i -> assertConfigMap(i, dataPrefix)));
  }

  private void assertConfigMap(int i, String prefix) {
    var cm = extension().get(ConfigMap.class, RESOURCE_NAME_PREFIX + i);
    assertThat(cm).isNotNull();
    assertThat(cm.getData().get(DATA_KEY)).isEqualTo(prefix + i);
  }

  private void createTestResources() {
    IntStream.range(0, NUMBER_OF_RESOURCE_TO_TEST)
        .forEach(
            i -> {
              extension().create(createTestResource(i));
            });
  }

  abstract P createTestResource(int index);

  abstract Class<P> customResourceClass();

  abstract LocallyRunOperatorExtension extension();
}
