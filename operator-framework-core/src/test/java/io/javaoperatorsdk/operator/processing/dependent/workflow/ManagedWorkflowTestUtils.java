package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Arrays;
import java.util.HashSet;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("rawtypes")
public class ManagedWorkflowTestUtils {

  public static DependentResourceSpec createDRS(String name, String... dependOns) {
    var drcMock = mock(DependentResourceSpec.class);
    when(drcMock.getName()).thenReturn(name);
    when(drcMock.getDependsOn()).thenReturn(new HashSet(Arrays.asList(dependOns)));
    return drcMock;
  }
}
