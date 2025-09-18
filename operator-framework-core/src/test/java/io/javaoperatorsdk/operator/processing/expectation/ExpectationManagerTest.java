package io.javaoperatorsdk.operator.processing.expectation;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpectationManagerTest {

  private ExpectationManager<ConfigMap> expectationManager;
  private ConfigMap configMap;
  private Context<ConfigMap> context;

  @BeforeEach
  void setUp() {
    expectationManager = new ExpectationManager<>();
    configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder().withName("test-configmap").withNamespace("test-namespace").build());
    context = mock(Context.class);
  }

  @Test
  void setExpectationShouldStoreExpectation() {
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    Duration timeout = Duration.ofMinutes(5);

    expectationManager.setExpectation(configMap, expectation, timeout);

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();
    assertThat(expectationManager.getExpectation(configMap)).contains(expectation);
  }

  @Test
  void checkOnExpectationShouldReturnEmptyWhenNoExpectation() {
    Optional<ExpectationResult<ConfigMap>> result =
        expectationManager.checkOnExpectation(configMap, context);

    assertThat(result).isEmpty();
  }

  @Test
  void checkOnExpectationShouldReturnFulfilledWhenExpectationMet() {
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    when(expectation.isFulfilled(configMap, context)).thenReturn(true);

    expectationManager.setExpectation(configMap, expectation, Duration.ofMinutes(5));
    Optional<ExpectationResult<ConfigMap>> result =
        expectationManager.checkOnExpectation(configMap, context);

    assertThat(result).isPresent();
    assertThat(result.get().status()).isEqualTo(ExpectationStatus.FULFILLED);
    assertThat(result.get().expectation()).isEqualTo(expectation);
    assertThat(expectationManager.isExpectationPresent(configMap)).isFalse();
  }

  @Test
  void checkOnExpectationShouldReturnNotFulfilledWhenExpectationNotMet() {
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    when(expectation.isFulfilled(configMap, context)).thenReturn(false);

    expectationManager.setExpectation(configMap, expectation, Duration.ofMinutes(5));
    Optional<ExpectationResult<ConfigMap>> result =
        expectationManager.checkOnExpectation(configMap, context);

    assertThat(result).isPresent();
    assertThat(result.get().status()).isEqualTo(ExpectationStatus.NOT_FULFILLED);
    assertThat(result.get().expectation()).isEqualTo(expectation);
    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();
  }

  @Test
  void checkOnExpectationShouldReturnTimedOutWhenExpectationExpired() throws InterruptedException {
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    when(expectation.isFulfilled(configMap, context)).thenReturn(false);

    expectationManager.setExpectation(configMap, expectation, Duration.ofMillis(1));
    Thread.sleep(10);
    Optional<ExpectationResult<ConfigMap>> result =
        expectationManager.checkOnExpectation(configMap, context);

    assertThat(result).isPresent();
    assertThat(result.get().status()).isEqualTo(ExpectationStatus.TIMED_OUT);
    assertThat(result.get().expectation()).isEqualTo(expectation);
    assertThat(expectationManager.isExpectationPresent(configMap)).isFalse();
  }

  @Test
  void getExpectationNameShouldReturnExpectationName() {
    String expectedName = "test-expectation";
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    when(expectation.name()).thenReturn(expectedName);

    expectationManager.setExpectation(configMap, expectation, Duration.ofMinutes(5));
    Optional<String> name = expectationManager.getExpectationName(configMap);

    assertThat(name).contains(expectedName);
  }

  @Test
  void getExpectationNameShouldReturnEmptyWhenNoExpectation() {
    Optional<String> name = expectationManager.getExpectationName(configMap);

    assertThat(name).isEmpty();
  }

  @Test
  void cleanupShouldRemoveExpectation() {
    Expectation<ConfigMap> expectation = mock(Expectation.class);

    expectationManager.setExpectation(configMap, expectation, Duration.ofMinutes(5));
    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();

    expectationManager.cleanup(configMap);
    assertThat(expectationManager.isExpectationPresent(configMap)).isFalse();
  }

  @Test
  void shouldHandleMultipleExpectationsForDifferentResources() {
    ConfigMap configMap2 = new ConfigMap();
    configMap2.setMetadata(
        new ObjectMetaBuilder()
            .withName("test-configmap-2")
            .withNamespace("test-namespace")
            .build());

    Expectation<ConfigMap> expectation1 = mock(Expectation.class);
    Expectation<ConfigMap> expectation2 = mock(Expectation.class);

    expectationManager.setExpectation(configMap, expectation1, Duration.ofMinutes(5));
    expectationManager.setExpectation(configMap2, expectation2, Duration.ofMinutes(5));

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();
    assertThat(expectationManager.isExpectationPresent(configMap2)).isTrue();
    assertThat(expectationManager.getExpectation(configMap)).contains(expectation1);
    assertThat(expectationManager.getExpectation(configMap2)).contains(expectation2);
  }

  @Test
  void setExpectationShouldReplaceExistingExpectation() {
    Expectation<ConfigMap> expectation1 = mock(Expectation.class);
    Expectation<ConfigMap> expectation2 = mock(Expectation.class);

    expectationManager.setExpectation(configMap, expectation1, Duration.ofMinutes(5));
    expectationManager.setExpectation(configMap, expectation2, Duration.ofMinutes(5));

    assertThat(expectationManager.getExpectation(configMap)).contains(expectation2);
  }
}
