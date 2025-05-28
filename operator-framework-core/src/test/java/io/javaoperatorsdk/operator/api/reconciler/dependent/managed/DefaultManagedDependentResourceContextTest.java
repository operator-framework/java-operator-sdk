package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultManagedDependentResourceContextTest {

  private final ManagedWorkflowAndDependentResourceContext context =
      new DefaultManagedWorkflowAndDependentResourceContext<>(null, null, null);

  @Test
  void getWhenEmpty() {
    Optional<String> actual = context.get("key", String.class);
    assertThat(actual).isEmpty();
  }

  @Test
  void get() {
    context.put("key", "value");
    Optional<String> actual = context.get("key", String.class);
    assertThat(actual).contains("value");
  }

  @Test
  void putNewValueOverwrites() {
    context.put("key", "value");
    context.put("key", "valueB");
    Optional<String> actual = context.get("key", String.class);
    assertThat(actual).contains("valueB");
  }

  @Test
  void putNewValueReturnsPriorValue() {
    final var prior = "value";
    context.put("key", prior);
    String actual = context.put("key", "valueB");
    assertThat(actual).isEqualTo(prior);
  }

  @Test
  void putNewValueLogsWarningIfTypesDiffer() {
    // to check that we properly log things without setting up a complex fixture
    final String[] messages = new String[1];
    var context =
        new DefaultManagedWorkflowAndDependentResourceContext<>(null, null, null) {
          @Override
          void logWarning(String message) {
            messages[0] = message;
          }
        };
    final var prior = "value";
    final var key = "key";
    context.put(key, prior);
    context.put(key, 10);
    assertThat(messages[0]).contains(key).contains(prior).contains("put(" + key + ", null)");
  }

  @Test
  void putNullRemoves() {
    context.put("key", "value");
    context.put("key", null);
    Optional<String> actual = context.get("key", String.class);
    assertThat(actual).isEmpty();
  }

  @Test
  void putNullReturnsPriorValue() {
    context.put("key", "value");
    String actual = context.put("key", null);
    assertThat(actual).contains("value");
  }

  @Test
  void getMandatory() {
    context.put("key", "value");
    String actual = context.getMandatory("key", String.class);
    assertThat(actual).isEqualTo("value");
  }

  @Test
  void getMandatoryWhenEmpty() {
    assertThatThrownBy(
            () -> {
              context.getMandatory("key", String.class);
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Mandatory attribute (key: key, type: java.lang.String) is missing or not of the"
                + " expected type");
  }
}
