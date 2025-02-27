package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

import static org.assertj.core.api.Assertions.assertThat;

class BaseWorkflowResultTest {
  private static final BaseWorkflowResult.Detail<?> detail =
      new BaseWorkflowResult.Detail<>(
          new RuntimeException(), null, null, null, null, null, false, false, false);

  @Test
  void throwsExceptionWithoutNumberingIfAllDifferentClass() {
    var res = new BaseWorkflowResult(Map.of(new DependentA(), detail, new DependentB(), detail));
    try {
      res.throwAggregateExceptionIfErrorsPresent();
    } catch (AggregatedOperatorException e) {
      assertThat(e.getAggregatedExceptions())
          .containsOnlyKeys(DependentA.class.getName(), DependentB.class.getName());
    }
  }

  @Test
  void numbersDependentClassNamesIfMoreOfSameType() {
    var res =
        new BaseWorkflowResult(
            Map.of(new DependentA("name1"), detail, new DependentA("name2"), detail));
    try {
      res.throwAggregateExceptionIfErrorsPresent();
    } catch (AggregatedOperatorException e) {
      assertThat(e.getAggregatedExceptions()).hasSize(2);
    }
  }

  @SuppressWarnings("rawtypes")
  static class DependentA implements DependentResource {

    private final String name;

    public DependentA() {
      this(null);
    }

    public DependentA(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      if (name == null) {
        return DependentResource.super.name();
      }
      return name;
    }

    @Override
    public ReconcileResult reconcile(HasMetadata primary, Context context) {
      return null;
    }

    @Override
    public Class resourceType() {
      return null;
    }
  }

  @SuppressWarnings("rawtypes")
  static class DependentB implements DependentResource {
    @Override
    public ReconcileResult reconcile(HasMetadata primary, Context context) {
      return null;
    }

    @Override
    public Class resourceType() {
      return null;
    }
  }
}
