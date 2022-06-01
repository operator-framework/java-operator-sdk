package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.data.Index;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("rawtypes")
class ManagedWorkflowUtilsTest {


  public static final String NAME_2 = "name2";
  public static final String NAME_1 = "name1";

  @Test
  void trivialCasesNameDuplicates() {
    ManagedWorkflowUtils.checkForNameDuplication(Collections.emptyList());
    ManagedWorkflowUtils.checkForNameDuplication(List.of(createDRS(NAME_1)));
    ManagedWorkflowUtils.checkForNameDuplication(List.of(createDRS(NAME_1), createDRS(NAME_2)));
  }

  @Test
  void checkFindsDuplicates() {
    Assertions.assertThrows(OperatorException.class, () -> ManagedWorkflowUtils
        .checkForNameDuplication(List.of(createDRS(NAME_2), createDRS(NAME_2))));

    Assertions.assertThrows(OperatorException.class,
        () -> ManagedWorkflowUtils.checkForNameDuplication(List.of(createDRS(NAME_1),
            createDRS(NAME_2),
            createDRS(NAME_2))));
  }

  @Test
  void orderingTrivialCases() {
    assertThat(ManagedWorkflowUtils.orderAndDetectCycles(List.of(createDRS(NAME_1))))
        .map(DependentResourceSpec::getName).containsExactly(NAME_1);

    assertThat(ManagedWorkflowUtils
        .orderAndDetectCycles(List.of(createDRS(NAME_2, NAME_1), createDRS(NAME_1))))
            .map(DependentResourceSpec::getName).containsExactly(NAME_1, NAME_2);
  }

  @Test
  void orderingDiamondShape() {
    String NAME_3 = "name3";
    String NAME_4 = "name4";

    var res = ManagedWorkflowUtils
        .orderAndDetectCycles(List.of(createDRS(NAME_2, NAME_1), createDRS(NAME_1),
            createDRS(NAME_3, NAME_1), createDRS(NAME_4, NAME_2, NAME_3)))
        .stream().map(DependentResourceSpec::getName).collect(Collectors.toList());

    assertThat(res)
        .containsExactlyInAnyOrder(NAME_1, NAME_2, NAME_3, NAME_4)
        .contains(NAME_1, Index.atIndex(0))
        .contains(NAME_4, Index.atIndex(3));
  }

  @Test
  void detectsCyclesTrivialCases() {
    String NAME_3 = "name3";
    Assertions.assertThrows(OperatorException.class, () -> ManagedWorkflowUtils
        .orderAndDetectCycles(List.of(createDRS(NAME_2, NAME_1), createDRS(NAME_1, NAME_2))));
    Assertions.assertThrows(OperatorException.class,
        () -> ManagedWorkflowUtils
            .orderAndDetectCycles(List.of(createDRS(NAME_2, NAME_1), createDRS(NAME_1, NAME_3),
                createDRS(NAME_3, NAME_2))));
  }

  @Test
  void detectsCycleOnSubTree() {
    String NAME_3 = "name3";
    String NAME_4 = "name4";
    Assertions.assertThrows(OperatorException.class,
        () -> ManagedWorkflowUtils.orderAndDetectCycles(List.of(createDRS(NAME_1),
            createDRS(NAME_2, NAME_1),
            createDRS(NAME_3, NAME_1, NAME_4),
            createDRS(NAME_4, NAME_3))));
  }

  private DependentResourceSpec createDRS(String name, String... dependOns) {
    var drcMock = createDRS(name);
    when(drcMock.getDependsOn()).thenReturn(new HashSet(Arrays.asList(dependOns)));
    return drcMock;
  }

  private DependentResourceSpec createDRS(String name) {
    var drc = mock(DependentResourceSpec.class);
    when(drc.getName()).thenReturn(name);
    return drc;
  }

}
