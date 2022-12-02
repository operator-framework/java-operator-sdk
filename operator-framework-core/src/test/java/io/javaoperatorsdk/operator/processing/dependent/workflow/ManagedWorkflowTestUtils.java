package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Arrays;
import java.util.Set;

import org.mockito.Mockito;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
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
    final var spy = Mockito.mock(DependentResourceSpec.class);
    when(spy.getName()).thenReturn(name);

    Class<? extends DependentResource> toMock = DependentResource.class;
    final var garbageCollected = dependentResourceTraits != null &&
        Arrays.asList(dependentResourceTraits).contains(GarbageCollected.class);

    final var dr = mock(toMock, withSettings().extraInterfaces(dependentResourceTraits));
    // it would be better to call the real method here but it doesn't work because
    // KubernetesDependentResource checks for GarbageCollected trait when instantiated which doesn't
    // happen when using mocks
    when(dr.isDeletable()).thenReturn(!garbageCollected);
    when(spy.getDependentResource()).thenReturn(dr);
    return spy;
  }

}
