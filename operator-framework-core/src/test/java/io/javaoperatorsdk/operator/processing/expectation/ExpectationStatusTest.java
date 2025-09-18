package io.javaoperatorsdk.operator.processing.expectation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectationStatusTest {

  @Test
  void shouldHaveThreeStatuses() {
    ExpectationStatus[] values = ExpectationStatus.values();

    assertThat(values).hasSize(3);
    assertThat(values)
        .containsExactlyInAnyOrder(
            ExpectationStatus.FULFILLED,
            ExpectationStatus.NOT_FULFILLED,
            ExpectationStatus.TIMED_OUT);
  }

  @Test
  void shouldHaveCorrectNames() {
    assertThat(ExpectationStatus.FULFILLED.name()).isEqualTo("FULFILLED");
    assertThat(ExpectationStatus.NOT_FULFILLED.name()).isEqualTo("NOT_FULFILLED");
    assertThat(ExpectationStatus.TIMED_OUT.name()).isEqualTo("TIMED_OUT");
  }

  @Test
  void shouldSupportValueOf() {
    assertThat(ExpectationStatus.valueOf("FULFILLED")).isEqualTo(ExpectationStatus.FULFILLED);
    assertThat(ExpectationStatus.valueOf("NOT_FULFILLED"))
        .isEqualTo(ExpectationStatus.NOT_FULFILLED);
    assertThat(ExpectationStatus.valueOf("TIMED_OUT")).isEqualTo(ExpectationStatus.TIMED_OUT);
  }
}
