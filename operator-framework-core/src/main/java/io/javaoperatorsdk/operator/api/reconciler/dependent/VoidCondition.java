package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

/** Used as default value for Condition in annotations */
@SuppressWarnings("rawtypes")
public class VoidCondition implements Condition {
  @Override
  public boolean isMet(DependentResource dependentResource, HasMetadata primary, Context context) {
    throw new IllegalStateException("This is a placeholder class, should not be called");
  }
}
