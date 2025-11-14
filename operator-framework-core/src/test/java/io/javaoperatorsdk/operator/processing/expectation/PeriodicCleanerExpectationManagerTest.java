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
package io.javaoperatorsdk.operator.processing.expectation;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.IndexedResourceCache;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

class PeriodicCleanerExpectationManagerTest {

  @Mock private IndexedResourceCache<ConfigMap> primaryCache;

  private PeriodicCleanerExpectationManager<ConfigMap> expectationManager;
  private ConfigMap configMap;
  private AutoCloseable closeable;

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder().withName("test-configmap").withNamespace("test-namespace").build());
  }

  @AfterEach
  void tearDown() throws Exception {
    if (expectationManager != null) {
      expectationManager.stop();
    }
    closeable.close();
  }

  @Test
  void shouldCleanExpectationsWhenResourceNotInCache() {
    Duration period = Duration.ofMillis(50);
    expectationManager = new PeriodicCleanerExpectationManager<>(period, primaryCache);

    ResourceID resourceId = ResourceID.fromResource(configMap);
    when(primaryCache.get(resourceId)).thenReturn(java.util.Optional.empty());

    Expectation<ConfigMap> expectation = (primary, context) -> false;
    expectationManager.setExpectation(configMap, Duration.ofMinutes(10), expectation);

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();

    await()
        .atMost(200, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> assertThat(expectationManager.isExpectationPresent(configMap)).isFalse());
  }

  @Test
  void shouldNotCleanExpectationsWhenResourceInCache() throws InterruptedException {
    Duration period = Duration.ofMillis(50);
    expectationManager = new PeriodicCleanerExpectationManager<>(period, primaryCache);

    ResourceID resourceId = ResourceID.fromResource(configMap);
    when(primaryCache.get(resourceId)).thenReturn(java.util.Optional.of(configMap));

    Expectation<ConfigMap> expectation = (primary, context) -> false;
    expectationManager.setExpectation(configMap, Duration.ofMinutes(10), expectation);

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();

    Thread.sleep(150);

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();
  }
}
