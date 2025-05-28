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

class ManagedWorkflowSupportTest {

  public static final String NAME_1 = "name1";
  public static final String NAME_2 = "name2";
  public static final String NAME_3 = "name3";
  public static final String NAME_4 = "name4";

  ManagedWorkflowSupport managedWorkflowSupport = new ManagedWorkflowSupport();

  @Test
  void trivialCasesNameDuplicates() {
    managedWorkflowSupport.checkForNameDuplication(null);
    managedWorkflowSupport.checkForNameDuplication(Collections.emptyList());
    managedWorkflowSupport.checkForNameDuplication(List.of(createDRS(NAME_1)));
    managedWorkflowSupport.checkForNameDuplication(List.of(createDRS(NAME_1), createDRS(NAME_2)));
  }

  @Test
  void checkFindsDuplicates() {
    final var drs2 = createDRS(NAME_2);
    final var drs1 = createDRS(NAME_1);

    Assertions.assertThrows(
        OperatorException.class,
        () -> managedWorkflowSupport.checkForNameDuplication(List.of(drs2, drs2)));

    Assertions.assertThrows(
        OperatorException.class,
        () -> managedWorkflowSupport.checkForNameDuplication(List.of(drs1, drs2, drs2)));

    final var exception =
        Assertions.assertThrows(
            OperatorException.class,
            () -> managedWorkflowSupport.checkForNameDuplication(List.of(drs1, drs2, drs2, drs1)));
    assertThat(exception.getMessage()).contains(NAME_1, NAME_2);
  }

  @Test
  void orderingTrivialCases() {
    assertThat(managedWorkflowSupport.orderAndDetectCycles(List.of(createDRS(NAME_1))))
        .map(DependentResourceSpec::getName)
        .containsExactly(NAME_1);

    assertThat(
            managedWorkflowSupport.orderAndDetectCycles(
                List.of(createDRS(NAME_2, NAME_1), createDRS(NAME_1))))
        .map(DependentResourceSpec::getName)
        .containsExactly(NAME_1, NAME_2);
  }

  @Test
  void orderingDiamondShape() {
    String NAME_3 = "name3";
    String NAME_4 = "name4";

    var res =
        managedWorkflowSupport
            .orderAndDetectCycles(
                List.of(
                    createDRS(NAME_2, NAME_1),
                    createDRS(NAME_1),
                    createDRS(NAME_3, NAME_1),
                    createDRS(NAME_4, NAME_2, NAME_3)))
            .stream()
            .map(DependentResourceSpec::getName)
            .collect(Collectors.toList());

    assertThat(res)
        .containsExactlyInAnyOrder(NAME_1, NAME_2, NAME_3, NAME_4)
        .contains(NAME_1, Index.atIndex(0))
        .contains(NAME_4, Index.atIndex(3));
  }

  @Test
  void orderingMultipleRoots() {
    final var NAME_3 = "name3";
    final var NAME_4 = "name4";
    final var NAME_5 = "name5";
    final var NAME_6 = "name6";

    var res =
        managedWorkflowSupport
            .orderAndDetectCycles(
                List.of(
                    createDRS(NAME_2, NAME_1, NAME_5),
                    createDRS(NAME_1),
                    createDRS(NAME_3, NAME_1),
                    createDRS(NAME_4, NAME_2, NAME_3),
                    createDRS(NAME_5, NAME_1, NAME_6),
                    createDRS(NAME_6)))
            .stream()
            .map(DependentResourceSpec::getName)
            .collect(Collectors.toList());

    assertThat(res)
        .containsExactlyInAnyOrder(NAME_1, NAME_5, NAME_6, NAME_2, NAME_3, NAME_4)
        .contains(NAME_6, Index.atIndex(0))
        .contains(NAME_1, Index.atIndex(1))
        .contains(NAME_5, Index.atIndex(2))
        .contains(NAME_3, Index.atIndex(3))
        .contains(NAME_2, Index.atIndex(4))
        .contains(NAME_4, Index.atIndex(5));
  }

  @Test
  void detectsCyclesTrivialCases() {
    String NAME_3 = "name3";
    Assertions.assertThrows(
        OperatorException.class,
        () ->
            managedWorkflowSupport.orderAndDetectCycles(
                List.of(createDRS(NAME_2, NAME_1), createDRS(NAME_1, NAME_2))));
    Assertions.assertThrows(
        OperatorException.class,
        () ->
            managedWorkflowSupport.orderAndDetectCycles(
                List.of(
                    createDRS(NAME_2, NAME_1),
                    createDRS(NAME_1, NAME_3),
                    createDRS(NAME_3, NAME_2))));
  }

  @Test
  void detectsCycleOnSubTree() {

    Assertions.assertThrows(
        OperatorException.class,
        () ->
            managedWorkflowSupport.orderAndDetectCycles(
                List.of(
                    createDRS(NAME_1),
                    createDRS(NAME_2, NAME_1),
                    createDRS(NAME_3, NAME_1, NAME_4),
                    createDRS(NAME_4, NAME_3))));

    Assertions.assertThrows(
        OperatorException.class,
        () ->
            managedWorkflowSupport.orderAndDetectCycles(
                List.of(
                    createDRS(NAME_1),
                    createDRS(NAME_2, NAME_1, NAME_4),
                    createDRS(NAME_3, NAME_2),
                    createDRS(NAME_4, NAME_3))));
  }

  @Test
  void createsWorkflow() {
    var specs =
        List.of(
            createDRS(NAME_1),
            createDRS(NAME_2, NAME_1),
            createDRS(NAME_3, NAME_1),
            createDRS(NAME_4, NAME_3, NAME_2));

    var workflow = managedWorkflowSupport.createAsDefault(specs);

    assertThat(workflow.nodeNames()).containsExactlyInAnyOrder(NAME_1, NAME_2, NAME_3, NAME_4);
    assertThat(workflow.getTopLevelResources()).containsExactly(NAME_1);
    assertThat(workflow.getBottomLevelResources()).containsExactly(NAME_4);
  }
}
