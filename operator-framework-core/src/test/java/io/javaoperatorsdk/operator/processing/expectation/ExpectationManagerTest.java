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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    expectationManager.setExpectation(configMap, timeout, expectation);

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();
    assertThat(expectationManager.getExpectation(configMap)).contains(expectation);
  }

  @Test
  void checkExpectationShouldReturnEmptyWhenNoExpectation() {
    ExpectationResult<ConfigMap> result = expectationManager.checkExpectation(configMap, context);

    assertThat(result.isExpectationPresent()).isFalse();
  }

  @Test
  void checkExpectationShouldReturnFulfilledWhenExpectationMet() {
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    when(expectation.isFulfilled(configMap, context)).thenReturn(true);

    expectationManager.setExpectation(configMap, Duration.ofMinutes(5), expectation);
    ExpectationResult<ConfigMap> result = expectationManager.checkExpectation(configMap, context);

    assertThat(result.isExpectationPresent()).isTrue();
    assertThat(result.isFulfilled()).isTrue();
    assertThat(result.expectation()).isEqualTo(expectation);
    assertThat(expectationManager.isExpectationPresent(configMap)).isFalse();
  }

  @Test
  void checkExpectationShouldReturnNotFulfilledWhenExpectationNotMet() {
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    when(expectation.isFulfilled(configMap, context)).thenReturn(false);

    expectationManager.setExpectation(configMap, Duration.ofMinutes(5), expectation);
    ExpectationResult<ConfigMap> result = expectationManager.checkExpectation(configMap, context);

    assertThat(result.isExpectationPresent()).isTrue();
    assertThat(result.isFulfilled()).isFalse();
    assertThat(result.expectation()).isEqualTo(expectation);
    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();
  }

  @Test
  void checkExpectationShouldReturnTimedOutWhenExpectationExpired() throws InterruptedException {
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    when(expectation.isFulfilled(configMap, context)).thenReturn(false);

    expectationManager.setExpectation(configMap, Duration.ofMillis(1), expectation);
    Thread.sleep(10);
    ExpectationResult<ConfigMap> result = expectationManager.checkExpectation(configMap, context);

    assertThat(result.isExpectationPresent()).isTrue();
    assertThat(result.isTimedOut()).isTrue();
    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();
  }

  @Test
  void getExpectationNameShouldReturnExpectationName() {
    String expectedName = "test-expectation";
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    when(expectation.name()).thenReturn(expectedName);

    expectationManager.setExpectation(configMap, Duration.ofMinutes(5), expectation);
    Optional<String> name = expectationManager.getExpectationName(configMap);

    assertThat(name).contains(expectedName);
  }

  @Test
  void getExpectationNameShouldReturnEmptyWhenNoExpectation() {
    Optional<String> name = expectationManager.getExpectationName(configMap);

    assertThat(name).isEmpty();
  }

  @Test
  void removeExpectationShouldRemoveExpectation() {
    Expectation<ConfigMap> expectation = mock(Expectation.class);

    expectationManager.setExpectation(configMap, Duration.ofMinutes(5), expectation);
    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();

    expectationManager.removeExpectation(configMap);
    assertThat(expectationManager.isExpectationPresent(configMap)).isFalse();
  }

  @Test
  void checkingSpecificExpectation() {
    String expectedName = "test-expectation";
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    when(expectation.name()).thenReturn(expectedName);
    when(expectation.isFulfilled(any(), any())).thenReturn(true);

    expectationManager.setExpectation(configMap, Duration.ofMinutes(1), expectation);

    var res = expectationManager.checkExpectation("other-expectation", configMap, context);
    assertThat(res.isExpectationPresent()).isFalse();
    res = expectationManager.checkExpectation(expectedName, configMap, context);
    assertThat(res.isExpectationPresent()).isTrue();
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

    expectationManager.setExpectation(configMap, Duration.ofMinutes(5), expectation1);
    expectationManager.setExpectation(configMap2, Duration.ofMinutes(5), expectation2);

    assertThat(expectationManager.isExpectationPresent(configMap)).isTrue();
    assertThat(expectationManager.isExpectationPresent(configMap2)).isTrue();
    assertThat(expectationManager.getExpectation(configMap)).contains(expectation1);
    assertThat(expectationManager.getExpectation(configMap2)).contains(expectation2);
  }

  @Test
  void setExpectationShouldReplaceExistingExpectation() {
    Expectation<ConfigMap> expectation1 = mock(Expectation.class);
    Expectation<ConfigMap> expectation2 = mock(Expectation.class);

    expectationManager.setExpectation(configMap, Duration.ofMinutes(5), expectation1);
    expectationManager.setExpectation(configMap, Duration.ofMinutes(5), expectation2);

    assertThat(expectationManager.getExpectation(configMap)).contains(expectation2);
  }

  @Test
  void checkAndSetExpectationAlreadyMet() {
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    when(expectation.isFulfilled(any(), any())).thenReturn(true);

    var res =
        expectationManager.checkAndSetExpectation(
            configMap, mock(Context.class), Duration.ofMinutes(5), expectation);
    assertThat(res).isFalse();
    assertThat(expectationManager.getExpectation(configMap)).isEmpty();
  }

  @Test
  void checkAndSetExpectationNotMet() {
    Expectation<ConfigMap> expectation = mock(Expectation.class);
    when(expectation.isFulfilled(any(), any())).thenReturn(false);

    var res =
        expectationManager.checkAndSetExpectation(
            configMap, mock(Context.class), Duration.ofMinutes(5), expectation);
    assertThat(res).isTrue();
    assertThat(expectationManager.getExpectation(configMap)).isPresent();
  }
}
