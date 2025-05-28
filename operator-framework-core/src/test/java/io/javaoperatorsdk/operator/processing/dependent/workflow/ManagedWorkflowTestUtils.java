package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Arrays;
import java.util.Set;

import org.mockito.Mockito;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.EmptyTestDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

import static org.mockito.Mockito.*;

@SuppressWarnings("rawtypes")
public class ManagedWorkflowTestUtils {

  @SuppressWarnings("unchecked")
  public static DependentResourceSpec createDRS(String name, String... dependOns) {
    return new DependentResourceSpec(
        EmptyTestDependentResource.class, name, Set.of(dependOns), null, null, null, null, null);
  }

  public static DependentResourceSpec createDRSWithTraits(
      String name, Class<?>... dependentResourceTraits) {
    final var spy = Mockito.mock(DependentResourceSpec.class);
    when(spy.getName()).thenReturn(name);

    Class<? extends DependentResource> toMock = DependentResource.class;
    final var garbageCollected =
        dependentResourceTraits != null
            && Arrays.asList(dependentResourceTraits).contains(GarbageCollected.class);
    if (garbageCollected) {
      toMock = KubernetesDependentResource.class;
    }

    final var dr = mock(toMock, withSettings().extraInterfaces(dependentResourceTraits));
    when(spy.getDependentResourceClass()).thenReturn(dr.getClass());
    return spy;
  }
}
