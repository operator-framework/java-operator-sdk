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
  void shouldCleanExpiredExpectationsWithCleanupDelay() {
    Duration period = Duration.ofMillis(50);
    Duration cleanupDelay = Duration.ofMillis(10);
    expectationManager = new PeriodicCleanerExpectationManager<>(period, cleanupDelay);

    Expectation<ConfigMap> expectation = (primary, context) -> false;
    expectationManager.setExpectation(configMap, expectation, Duration.ofMillis(1));

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();

    await()
        .atMost(200, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> assertThat(expectationManager.isExpectationPresent(configMap)).isFalse());
  }

  @Test
  void shouldCleanExpectationsWhenResourceNotInCache() {
    Duration period = Duration.ofMillis(50);
    expectationManager = new PeriodicCleanerExpectationManager<>(period, primaryCache);

    ResourceID resourceId = ResourceID.fromResource(configMap);
    when(primaryCache.get(resourceId)).thenReturn(java.util.Optional.empty());

    Expectation<ConfigMap> expectation = (primary, context) -> false;
    expectationManager.setExpectation(configMap, expectation, Duration.ofMinutes(10));

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
    expectationManager.setExpectation(configMap, expectation, Duration.ofMinutes(10));

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();

    Thread.sleep(150);

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();
  }

  @Test
  void shouldNotCleanNonExpiredExpectationsWithCleanupDelay() throws InterruptedException {
    Duration period = Duration.ofMillis(50);
    Duration cleanupDelay = Duration.ofMinutes(1);
    expectationManager = new PeriodicCleanerExpectationManager<>(period, cleanupDelay);

    Expectation<ConfigMap> expectation = (primary, context) -> false;
    expectationManager.setExpectation(configMap, expectation, Duration.ofMillis(1));

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();

    Thread.sleep(150);

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();
  }

  @Test
  void stopShouldShutdownScheduler() {
    Duration period = Duration.ofMillis(50);
    expectationManager = new PeriodicCleanerExpectationManager<>(period, Duration.ofMillis(10));

    expectationManager.stop();

    Expectation<ConfigMap> expectation = (primary, context) -> false;
    expectationManager.setExpectation(configMap, expectation, Duration.ofMillis(1));

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();
  }

  @Test
  void cleanShouldWorkDirectly() {
    Duration period = Duration.ofMinutes(10);
    Duration cleanupDelay = Duration.ofMillis(1);
    expectationManager = new PeriodicCleanerExpectationManager<>(period, cleanupDelay);

    Expectation<ConfigMap> expectation = (primary, context) -> false;
    expectationManager.setExpectation(configMap, expectation, Duration.ofMillis(1));

    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();

    expectationManager.clean();

    assertThat(expectationManager.isExpectationPresent(configMap)).isFalse();
  }
}
