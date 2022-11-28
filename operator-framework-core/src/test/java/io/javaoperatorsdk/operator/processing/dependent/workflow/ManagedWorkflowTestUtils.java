package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Set;

import org.mockito.Mockito;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.EmptyTestDependentResource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@SuppressWarnings("rawtypes")
public class ManagedWorkflowTestUtils {

  @SuppressWarnings("unchecked")
  public static DependentResourceSpec createDRS(String name, String... dependOns) {
    return new DependentResourceSpec(new EmptyTestDependentResource(), name, Set.of(dependOns),
        null, null, null, null);
  }

  public static DependentResourceSpec createDRSWithTraits(String name,
      Class<?>... dependentResourceTraits) {
    final var drs = createDRS(name);
    final var spy = Mockito.spy(drs);
    when(spy.getDependentResource())
        .thenReturn(
            mock(DependentResource.class, withSettings().extraInterfaces(dependentResourceTraits)));
    return spy;
  }

}
