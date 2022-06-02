package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.data.Index;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowTestUtils.createDRS;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("rawtypes")
class ManagedWorkflowSupportTest {


  public static final String NAME_2 = "name2";
  public static final String NAME_1 = "name1";

  ManagedWorkflowSupport<?> managedWorkflowSupport = new ManagedWorkflowSupport();

  @Test
  void trivialCasesNameDuplicates() {
    managedWorkflowSupport.checkForNameDuplication(Collections.emptyList());
    managedWorkflowSupport.checkForNameDuplication(List.of(createDRS(NAME_1)));
    managedWorkflowSupport.checkForNameDuplication(List.of(createDRS(NAME_1), createDRS(NAME_2)));
  }

  @Test
  void checkFindsDuplicates() {
    Assertions.assertThrows(OperatorException.class, () -> managedWorkflowSupport
        .checkForNameDuplication(List.of(createDRS(NAME_2), createDRS(NAME_2))));

    Assertions.assertThrows(OperatorException.class,
        () -> managedWorkflowSupport.checkForNameDuplication(List.of(createDRS(NAME_1),
            createDRS(NAME_2),
            createDRS(NAME_2))));
  }

  @Test
  void orderingTrivialCases() {
    assertThat(managedWorkflowSupport.orderAndDetectCycles(List.of(createDRS(NAME_1))))
        .map(DependentResourceSpec::getName).containsExactly(NAME_1);

    assertThat(managedWorkflowSupport
        .orderAndDetectCycles(List.of(createDRS(NAME_2, NAME_1), createDRS(NAME_1))))
            .map(DependentResourceSpec::getName).containsExactly(NAME_1, NAME_2);
  }

  @Test
  void orderingDiamondShape() {
    String NAME_3 = "name3";
    String NAME_4 = "name4";

    var res = managedWorkflowSupport
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
    Assertions.assertThrows(OperatorException.class, () -> managedWorkflowSupport
        .orderAndDetectCycles(List.of(createDRS(NAME_2, NAME_1), createDRS(NAME_1, NAME_2))));
    Assertions.assertThrows(OperatorException.class,
        () -> managedWorkflowSupport
            .orderAndDetectCycles(List.of(createDRS(NAME_2, NAME_1), createDRS(NAME_1, NAME_3),
                createDRS(NAME_3, NAME_2))));
  }

  @Test
  void detectsCycleOnSubTree() {
    String NAME_3 = "name3";
    String NAME_4 = "name4";
    Assertions.assertThrows(OperatorException.class,
        () -> managedWorkflowSupport.orderAndDetectCycles(List.of(createDRS(NAME_1),
            createDRS(NAME_2, NAME_1),
            createDRS(NAME_3, NAME_1, NAME_4),
            createDRS(NAME_4, NAME_3))));
  }


}
