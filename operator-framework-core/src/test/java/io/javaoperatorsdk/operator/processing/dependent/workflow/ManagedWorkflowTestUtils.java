package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Set;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.processing.dependent.EmptyTestDependentResource;

@SuppressWarnings("rawtypes")
public class ManagedWorkflowTestUtils {

  @SuppressWarnings("unchecked")
  public static DependentResourceSpec createDRS(String name, String... dependOns) {
    return new DependentResourceSpec(EmptyTestDependentResource.class,
        null, name, Set.of(dependOns), null, null, null);
  }

}
