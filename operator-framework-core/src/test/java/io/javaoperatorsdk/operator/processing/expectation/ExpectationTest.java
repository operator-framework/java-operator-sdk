package io.javaoperatorsdk.operator.processing.expectation;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExpectationTest {

  @Test
  void createExpectationWithCustomName() {
    String customName = "test-expectation";
    Expectation<ConfigMap> expectation =
        Expectation.createExpectation(customName, (primary, context) -> true);

    assertThat(expectation.name()).isEqualTo(customName);
  }

  @Test
  void createExpectationWithPredicate() {
    ConfigMap configMap = new ConfigMap();
    Context<ConfigMap> context = mock(Context.class);

    Expectation<ConfigMap> trueExpectation =
        Expectation.createExpectation("always-true", (primary, ctx) -> true);
    Expectation<ConfigMap> falseExpectation =
        Expectation.createExpectation("always-false", (primary, ctx) -> false);

    assertThat(trueExpectation.isFulfilled(configMap, context)).isTrue();
    assertThat(falseExpectation.isFulfilled(configMap, context)).isFalse();
  }

  @Test
  void expectationShouldWorkWithGenericTypes() {
    ConfigMap configMap = new ConfigMap();
    Context<ConfigMap> context = mock(Context.class);

    Expectation<ConfigMap> expectation =
        new Expectation<>() {
          @Override
          public String name() {
            return "custom-expectation";
          }

          @Override
          public boolean isFulfilled(ConfigMap primary, Context<ConfigMap> context) {
            return primary != null;
          }
        };

    assertThat(expectation.name()).isEqualTo("custom-expectation");
    assertThat(expectation.isFulfilled(configMap, context)).isTrue();
    assertThat(expectation.isFulfilled(null, context)).isFalse();
  }
}
