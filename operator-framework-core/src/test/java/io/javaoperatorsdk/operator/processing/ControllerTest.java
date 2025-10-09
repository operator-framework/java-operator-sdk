/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowFactory;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowCleanupResult;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.api.monitoring.Metrics.NOOP;
import static io.javaoperatorsdk.operator.processing.Controller.CLEANER_NOT_SUPPORTED_ON_ALL_EVENT_ERROR_MESSAGE;
import static io.javaoperatorsdk.operator.processing.Controller.MANAGED_WORKFLOWS_NOT_SUPPORTED_TRIGGER_RECONCILER_ON_ALL_EVENT_ERROR_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@SuppressWarnings({"unchecked", "rawtypes"})
class ControllerTest {

  final Reconciler reconciler = mock(Reconciler.class);

  ConfigurationService configurationService = new BaseConfigurationService();

  @Test
  void crdShouldNotBeCheckedForNativeResources() {
    final var client = MockKubernetesClient.client(Secret.class);
    final var configuration =
        MockControllerConfiguration.forResource(Secret.class, configurationService);
    final var controller = new Controller<Secret>(reconciler, configuration, client);
    controller.start();
    verify(client, never()).apiextensions();
  }

  @Test
  void crdShouldNotBeCheckedForCustomResourcesIfDisabled() {
    final var client = MockKubernetesClient.client(TestCustomResource.class);
    ConfigurationService configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(), o -> o.checkingCRDAndValidateLocalModel(false));

    final var configuration =
        MockControllerConfiguration.forResource(TestCustomResource.class, configurationService);
    final var controller = new Controller<TestCustomResource>(reconciler, configuration, client);
    controller.start();
    verify(client, never()).apiextensions();
  }

  @Test
  void usesFinalizerIfThereIfReconcilerImplementsCleaner() {
    Reconciler reconciler = mock(Reconciler.class, withSettings().extraInterfaces(Cleaner.class));
    final var configuration = MockControllerConfiguration.forResource(Secret.class);
    when(configuration.getConfigurationService()).thenReturn(new BaseConfigurationService());

    final var controller =
        new Controller<Secret>(
            reconciler, configuration, MockKubernetesClient.client(Secret.class));

    assertThat(controller.useFinalizer()).isTrue();
  }

  @Test
  void cleanerNotAllowedWithTriggerOnAllEventEnabled() {
    Reconciler reconciler = mock(Reconciler.class, withSettings().extraInterfaces(Cleaner.class));
    final var configuration = MockControllerConfiguration.forResource(Secret.class);
    when(configuration.getConfigurationService()).thenReturn(new BaseConfigurationService());
    when(configuration.triggerReconcilerOnAllEvent()).thenReturn(true);

    var exception =
        assertThrows(
            OperatorException.class,
            () ->
                new Controller<Secret>(
                    reconciler, configuration, MockKubernetesClient.client(Secret.class)));

    assertThat(exception.getMessage()).isEqualTo(CLEANER_NOT_SUPPORTED_ON_ALL_EVENT_ERROR_MESSAGE);
  }

  @Test
  void managedWorkflowNotAllowedWithOnAllEventEnabled() {
    Reconciler reconciler = mock(Reconciler.class);
    final var configuration = MockControllerConfiguration.forResource(Secret.class);

    var configurationService = mock(ConfigurationService.class);
    var mockWorkflowFactory = mock(ManagedWorkflowFactory.class);
    var mockManagedWorkflow = mock(ManagedWorkflow.class);

    when(configuration.getConfigurationService()).thenReturn(configurationService);
    var workflowSpec = mock(WorkflowSpec.class);
    when(configuration.getWorkflowSpec()).thenReturn(Optional.of(workflowSpec));
    when(configurationService.getMetrics()).thenReturn(NOOP);
    when(configurationService.getWorkflowFactory()).thenReturn(mockWorkflowFactory);
    when(mockWorkflowFactory.workflowFor(any())).thenReturn(mockManagedWorkflow);
    var managedWorkflowMock = workflow(true);
    when(mockManagedWorkflow.resolve(any(), any())).thenReturn(managedWorkflowMock);

    when(configuration.triggerReconcilerOnAllEvent()).thenReturn(true);

    var exception =
        assertThrows(
            OperatorException.class,
            () ->
                new Controller<Secret>(
                    reconciler, configuration, MockKubernetesClient.client(Secret.class)));

    assertThat(exception.getMessage())
        .isEqualTo(MANAGED_WORKFLOWS_NOT_SUPPORTED_TRIGGER_RECONCILER_ON_ALL_EVENT_ERROR_MESSAGE);
  }

  @ParameterizedTest
  @CsvSource({
    "true, true, true, false",
    "true, true, false, true",
    "false, true, true, true",
    "false, true, false, true",
    "true, false, true, false",
  })
  void callsCleanupOnWorkflowWhenHasCleanerAndReconcilerIsNotCleaner(
      boolean reconcilerIsCleaner,
      boolean workflowIsCleaner,
      boolean isExplicitWorkflowInvocation,
      boolean workflowCleanerExecuted)
      throws Exception {

    Reconciler reconciler;
    if (reconcilerIsCleaner) {
      reconciler = mock(Reconciler.class, withSettings().extraInterfaces(Cleaner.class));
    } else {
      reconciler = mock(Reconciler.class);
    }

    final var configuration = MockControllerConfiguration.forResource(Secret.class);

    if (reconciler instanceof Cleaner<?> cleaner) {
      when(cleaner.cleanup(any(), any())).thenReturn(DeleteControl.noFinalizerRemoval());
    }

    var configurationService = mock(ConfigurationService.class);
    var mockWorkflowFactory = mock(ManagedWorkflowFactory.class);
    var mockManagedWorkflow = mock(ManagedWorkflow.class);

    when(configuration.getConfigurationService()).thenReturn(configurationService);
    var workflowSpec = mock(WorkflowSpec.class);
    when(workflowSpec.isExplicitInvocation()).thenReturn(isExplicitWorkflowInvocation);
    when(configuration.getWorkflowSpec()).thenReturn(Optional.of(workflowSpec));
    when(configurationService.getMetrics()).thenReturn(NOOP);
    when(configurationService.getWorkflowFactory()).thenReturn(mockWorkflowFactory);
    when(mockWorkflowFactory.workflowFor(any())).thenReturn(mockManagedWorkflow);
    var managedWorkflowMock = workflow(workflowIsCleaner);
    when(mockManagedWorkflow.resolve(any(), any())).thenReturn(managedWorkflowMock);

    final var controller =
        new Controller<Secret>(
            reconciler, configuration, MockKubernetesClient.client(Secret.class));

    controller.cleanup(
        new Secret(), new DefaultContext<>(null, controller, new Secret(), false, false));

    verify(managedWorkflowMock, times(workflowCleanerExecuted ? 1 : 0)).cleanup(any(), any());
  }

  private Workflow workflow(boolean hasCleaner) {
    var workflow = mock(Workflow.class);
    when(workflow.cleanup(any(), any())).thenReturn(mock(WorkflowCleanupResult.class));
    when(workflow.hasCleaner()).thenReturn(hasCleaner);
    when(workflow.isEmpty()).thenReturn(false);
    return workflow;
  }
}
