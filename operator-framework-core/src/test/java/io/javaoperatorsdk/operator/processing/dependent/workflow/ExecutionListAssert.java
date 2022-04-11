package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;

import org.assertj.core.api.AbstractAssert;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class ExecutionListAssert
    extends AbstractAssert<ExecutionListAssert, List<DependentResource<?, ?>>> {

  public ExecutionListAssert(List<DependentResource<?, ?>> dependentResources) {
    super(dependentResources, ExecutionListAssert.class);
  }

  public static ExecutionListAssert assertThat(List<DependentResource<?, ?>> actual) {
    return new ExecutionListAssert(actual);
  }



}
