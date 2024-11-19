package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;

import static io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DefaultManagedDependentResourceContext.RECONCILE_RESULT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultManagedDependentResourceContextTest {

  private final ManagedDependentResourceContext context =
      new DefaultManagedDependentResourceContext();

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
  void putNewValueThrowsExceptionIfTypesDiffer() {
    // to check that we properly log things without setting up a complex fixture
    final String[] messages = new String[1];
    var context = new DefaultManagedDependentResourceContext() {
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
    assertThatThrownBy(() -> {
      context.getMandatory("key", String.class);
    }).isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Mandatory attribute (key: key, type: java.lang.String) is missing or not of the expected type");
  }

  @Test
  void getWorkflowReconcileResult() {
    WorkflowReconcileResult result =
        new WorkflowReconcileResult(List.of(), List.of(), Map.of(), Map.of());
    context.put(RECONCILE_RESULT_KEY, result);
    Optional<WorkflowReconcileResult> actual = context.getWorkflowReconcileResult();
    assertThat(actual).containsSame(result);
  }
}
