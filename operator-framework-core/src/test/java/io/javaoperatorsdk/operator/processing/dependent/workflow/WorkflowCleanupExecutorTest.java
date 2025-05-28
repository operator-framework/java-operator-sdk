package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedWorkflowAndDependentResourceContext;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ExecutionAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WorkflowCleanupExecutorTest extends AbstractWorkflowExecutorTest {

  protected TestDeleterDependent dd1 = new TestDeleterDependent("DR_DELETER_1");
  protected TestDeleterDependent dd2 = new TestDeleterDependent("DR_DELETER_2");
  protected TestDeleterDependent dd3 = new TestDeleterDependent("DR_DELETER_3");
  protected TestDeleterDependent dd4 = new TestDeleterDependent("DR_DELETER_4");

  @SuppressWarnings("unchecked")
  Context<TestCustomResource> mockContext = spy(Context.class);

  ExecutorService executorService = Executors.newCachedThreadPool();

  @BeforeEach
  void setup() {
    var eventSourceContextMock = mock(EventSourceContext.class);
    var eventSourceRetrieverMock = mock(EventSourceRetriever.class);
    var mockControllerConfig = mock(ControllerConfiguration.class);
    when(eventSourceRetrieverMock.eventSourceContextForDynamicRegistration())
        .thenReturn(eventSourceContextMock);
    var client = MockKubernetesClient.client(ConfigMap.class);
    when(eventSourceContextMock.getClient()).thenReturn(client);
    when(eventSourceContextMock.getControllerConfiguration()).thenReturn(mockControllerConfig);
    when(mockControllerConfig.getConfigurationService())
        .thenReturn(mock(ConfigurationService.class));
    when(mockContext.managedWorkflowAndDependentResourceContext())
        .thenReturn(mock(ManagedWorkflowAndDependentResourceContext.class));
    when(mockContext.getWorkflowExecutorService()).thenReturn(executorService);
    when(mockContext.eventSourceRetriever()).thenReturn(eventSourceRetrieverMock);
  }

  @Test
  void cleanUpDiamondWorkflow() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dd1)
            .addDependentResourceAndConfigure(dr1)
            .dependsOn(dd1)
            .addDependentResourceAndConfigure(dd2)
            .dependsOn(dd1)
            .addDependentResourceAndConfigure(dd3)
            .dependsOn(dr1, dd2)
            .build();

    var res = workflow.cleanup(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciledInOrder(dd3, dd2, dd1).notReconciled(dr1);

    Assertions.assertThat(res.getDeleteCalledOnDependents())
        .containsExactlyInAnyOrder(dd1, dd2, dd3);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getPostConditionNotMetDependents()).isEmpty();
  }

  @Test
  void dontDeleteIfDependentErrored() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dd1)
            .addDependentResourceAndConfigure(dd2)
            .dependsOn(dd1)
            .addDependentResourceAndConfigure(dd3)
            .dependsOn(dd2)
            .addDependentResourceAndConfigure(errorDD)
            .dependsOn(dd2)
            .withThrowExceptionFurther(false)
            .build();

    var res = workflow.cleanup(new TestCustomResource(), mockContext);
    assertThrows(AggregatedOperatorException.class, res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).deleted(dd3, errorDD).notReconciled(dd1, dd2);

    Assertions.assertThat(res.getDeleteCalledOnDependents()).containsExactlyInAnyOrder(dd3);
    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(errorDD);
    Assertions.assertThat(res.getPostConditionNotMetDependents()).isEmpty();
  }

  @Test
  void cleanupConditionTrivialCase() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dd1)
            .addDependentResourceAndConfigure(dd2)
            .dependsOn(dd1)
            .withDeletePostcondition(notMetCondition)
            .build();

    var res = workflow.cleanup(new TestCustomResource(), mockContext);

    assertThat(executionHistory).deleted(dd2).notReconciled(dd1);
    Assertions.assertThat(res.getDeleteCalledOnDependents()).containsExactlyInAnyOrder(dd2);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getPostConditionNotMetDependents()).containsExactlyInAnyOrder(dd2);
  }

  @Test
  void cleanupConditionMet() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dd1)
            .addDependentResourceAndConfigure(dd2)
            .dependsOn(dd1)
            .withDeletePostcondition(metCondition)
            .build();

    var res = workflow.cleanup(new TestCustomResource(), mockContext);

    assertThat(executionHistory).deleted(dd2, dd1);

    Assertions.assertThat(res.getDeleteCalledOnDependents()).containsExactlyInAnyOrder(dd1, dd2);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getPostConditionNotMetDependents()).isEmpty();
  }

  @Test
  void cleanupConditionDiamondWorkflow() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dd1)
            .addDependentResourceAndConfigure(dd2)
            .dependsOn(dd1)
            .addDependentResourceAndConfigure(dd3)
            .dependsOn(dd1)
            .withDeletePostcondition(notMetCondition)
            .addDependentResourceAndConfigure(dd4)
            .dependsOn(dd2, dd3)
            .build();

    var res = workflow.cleanup(new TestCustomResource(), mockContext);

    assertThat(executionHistory)
        .reconciledInOrder(dd4, dd2)
        .reconciledInOrder(dd4, dd3)
        .notReconciled(dr1);

    Assertions.assertThat(res.getDeleteCalledOnDependents())
        .containsExactlyInAnyOrder(dd4, dd3, dd2);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getPostConditionNotMetDependents()).containsExactlyInAnyOrder(dd3);
  }

  @Test
  void dontDeleteIfGarbageCollected() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>().addDependentResource(gcDeleter).build();

    var res = workflow.cleanup(new TestCustomResource(), mockContext);

    assertThat(executionHistory).notReconciled(gcDeleter);

    Assertions.assertThat(res.getDeleteCalledOnDependents()).isEmpty();
  }

  @Test
  void ifDependentActiveDependentNormallyDeleted() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dd1)
            .addDependentResourceAndConfigure(dd2)
            .dependsOn(dd1)
            .addDependentResourceAndConfigure(dd3)
            .dependsOn(dd1)
            .withActivationCondition(metCondition)
            .addDependentResourceAndConfigure(dd4)
            .dependsOn(dd2, dd3)
            .build();

    var res = workflow.cleanup(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciledInOrder(dd4, dd2, dd1).reconciledInOrder(dd4, dd3, dd1);

    Assertions.assertThat(res.getDeleteCalledOnDependents())
        .containsExactlyInAnyOrder(dd4, dd3, dd2, dd1);
  }

  @Test
  void ifDependentActiveDeletePostConditionIsChecked() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dd1)
            .addDependentResourceAndConfigure(dd2)
            .dependsOn(dd1)
            .addDependentResourceAndConfigure(dd3)
            .dependsOn(dd1)
            .withDeletePostcondition(notMetCondition)
            .withActivationCondition(metCondition)
            .addDependentResourceAndConfigure(dd4)
            .dependsOn(dd2, dd3)
            .build();

    var res = workflow.cleanup(new TestCustomResource(), mockContext);

    assertThat(executionHistory)
        .reconciledInOrder(dd4, dd2)
        .reconciledInOrder(dd4, dd3)
        .notReconciled(dr1);

    Assertions.assertThat(res.getDeleteCalledOnDependents())
        .containsExactlyInAnyOrder(dd4, dd3, dd2);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getPostConditionNotMetDependents()).containsExactlyInAnyOrder(dd3);
  }

  @Test
  void ifDependentInactiveDeleteIsNotCalled() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dd1)
            .addDependentResourceAndConfigure(dd2)
            .dependsOn(dd1)
            .addDependentResourceAndConfigure(dd3)
            .dependsOn(dd1)
            .withActivationCondition(notMetCondition)
            .addDependentResourceAndConfigure(dd4)
            .dependsOn(dd2, dd3)
            .build();

    var res = workflow.cleanup(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciledInOrder(dd4, dd2, dd1);

    Assertions.assertThat(res.getDeleteCalledOnDependents())
        .containsExactlyInAnyOrder(dd4, dd2, dd1);
  }

  @Test
  void ifDependentInactiveDeletePostConditionNotChecked() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dd1)
            .addDependentResourceAndConfigure(dd2)
            .dependsOn(dd1)
            .addDependentResourceAndConfigure(dd3)
            .dependsOn(dd1)
            .withDeletePostcondition(notMetCondition)
            .withActivationCondition(notMetCondition)
            .addDependentResourceAndConfigure(dd4)
            .dependsOn(dd2, dd3)
            .build();

    var res = workflow.cleanup(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciledInOrder(dd4, dd2, dd1);

    Assertions.assertThat(res.getPostConditionNotMetDependents()).isEmpty();
  }

  @Test
  void singleInactiveDependent() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dd1)
            .withActivationCondition(notMetCondition)
            .build();

    workflow.cleanup(new TestCustomResource(), mockContext);

    assertThat(executionHistory).notReconciled(dd1);
  }
}
