package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.RetryInfo;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.sample.observedgeneration.ObservedGenCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventDispatcherTest {

  private static final String DEFAULT_FINALIZER = "javaoperatorsdk.io/finalizer";
  private TestCustomResource testCustomResource;
  private EventDispatcher eventDispatcher;
  private final ResourceController<TestCustomResource> controller = mock(ResourceController.class);
  private final ControllerConfiguration<TestCustomResource> configuration =
          mock(ControllerConfiguration.class);
  private final ConfigurationService configService = mock(ConfigurationService.class);
  private final ConfiguredController<CustomResource<?, ?>> configuredController =
          new ConfiguredController(controller, configuration, null);
  private final EventDispatcher.CustomResourceFacade customResourceFacade =
          mock(EventDispatcher.CustomResourceFacade.class);

  @BeforeEach
  void setup() {
    eventDispatcher = new EventDispatcher(configuredController, customResourceFacade);

    testCustomResource = TestUtils.testCustomResource();

    when(configuration.getFinalizer()).thenReturn(DEFAULT_FINALIZER);
    when(configuration.useFinalizer()).thenCallRealMethod();
    when(configuration.getName()).thenReturn("EventDispatcherTestController");
    when(configService.getMetrics()).thenReturn(Metrics.NOOP);
    when(configuration.getConfigurationService()).thenReturn(configService);
    when(controller.createOrUpdateResource(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.updateCustomResource(testCustomResource));
    when(controller.deleteResource(eq(testCustomResource), any()))
        .thenReturn(DeleteControl.defaultDelete());
    when(customResourceFacade.replaceWithLock(any())).thenReturn(null);
  }

  @Test
  void addFinalizerOnNewResource() {
    assertFalse(testCustomResource.hasFinalizer(DEFAULT_FINALIZER));
    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));
    verify(controller, never())
            .createOrUpdateResource(ArgumentMatchers.eq(testCustomResource), any());
    verify(customResourceFacade, times(1))
        .replaceWithLock(
            argThat(testCustomResource -> testCustomResource.hasFinalizer(DEFAULT_FINALIZER)));
    assertTrue(testCustomResource.hasFinalizer(DEFAULT_FINALIZER));
  }

  @Test
  void callCreateOrUpdateOnNewResourceIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));
    verify(controller, times(1))
            .createOrUpdateResource(ArgumentMatchers.eq(testCustomResource), any());
  }

  @Test
  void updatesOnlyStatusSubResourceIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(controller.createOrUpdateResource(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.updateStatusSubResource(testCustomResource));

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
    verify(customResourceFacade, never()).replaceWithLock(any());
  }

  @Test
  void updatesBothResourceAndStatusIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(controller.createOrUpdateResource(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.updateCustomResourceAndStatus(testCustomResource));
    when(customResourceFacade.replaceWithLock(testCustomResource)).thenReturn(testCustomResource);

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, times(1)).replaceWithLock(testCustomResource);
    verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
  }

  @Test
  void callCreateOrUpdateOnModifiedResourceIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));
    verify(controller, times(1))
            .createOrUpdateResource(ArgumentMatchers.eq(testCustomResource), any());
  }

  @Test
  void callsDeleteIfObjectHasFinalizerAndMarkedForDelete() {
    // we need to add the finalizer before marking it for deletion, as otherwise it won't get added
    assertTrue(testCustomResource.addFinalizer(DEFAULT_FINALIZER));
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    verify(controller, times(1)).deleteResource(eq(testCustomResource), any());
  }

  /** Note that there could be more finalizers. Out of our control. */
  @Test
  void callDeleteOnControllerIfMarkedForDeletionWhenNoFinalizerIsConfigured() {
    configureToNotUseFinalizer();
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    verify(controller).deleteResource(eq(testCustomResource), any());
  }

  @Test
  void doNotCallDeleteIfMarkedForDeletionWhenFinalizerHasAlreadyBeenRemoved() {
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    verify(controller, never()).deleteResource(eq(testCustomResource), any());
  }

  private void configureToNotUseFinalizer() {
    ControllerConfiguration<CustomResource<?, ?>> configuration =
            mock(ControllerConfiguration.class);
    when(configuration.getName()).thenReturn("EventDispatcherTestController");
    when(configService.getMetrics()).thenReturn(Metrics.NOOP);
    when(configuration.getConfigurationService()).thenReturn(configService);
    when(configuration.useFinalizer()).thenReturn(false);
    eventDispatcher = new EventDispatcher(new ConfiguredController(controller, configuration, null),
            customResourceFacade);
  }

  @Test
  void doesNotAddFinalizerIfConfiguredNotTo() {
    configureToNotUseFinalizer();

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
  }

  @Test
  void removesDefaultFinalizerOnDeleteIfSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, times(1)).replaceWithLock(any());
  }

  @Test
  void doesNotRemovesTheSetFinalizerIfTheDeleteNotMethodInstructsIt() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(controller.deleteResource(eq(testCustomResource), any()))
        .thenReturn(DeleteControl.noFinalizerRemoval());
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, never()).replaceWithLock(any());
  }

  @Test
  void doesNotUpdateTheResourceIfNoUpdateUpdateControlIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(controller.createOrUpdateResource(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.noUpdate());

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));
    verify(customResourceFacade, never()).replaceWithLock(any());
    verify(customResourceFacade, never()).updateStatus(testCustomResource);
  }

  @Test
  void addsFinalizerIfNotMarkedForDeletionAndEmptyCustomResourceReturned() {
    removeFinalizers(testCustomResource);
    when(controller.createOrUpdateResource(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.noUpdate());

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, times(1)).replaceWithLock(any());
  }

  @Test
  void doesNotCallDeleteIfMarkedForDeletionButNotOurFinalizer() {
    removeFinalizers(testCustomResource);
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, never()).replaceWithLock(any());
    verify(controller, never()).deleteResource(eq(testCustomResource), any());
  }

  @Test
  void executeControllerRegardlessGenerationInNonGenerationAwareModeIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));
    eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    verify(controller, times(2)).createOrUpdateResource(eq(testCustomResource), any());
  }

  @Test
  void propagatesRetryInfoToContextIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    eventDispatcher.handleExecution(
        new ExecutionScope(
            testCustomResource,
            new RetryInfo() {
              @Override
              public int getAttemptCount() {
                return 2;
              }

              @Override
              public boolean isLastAttempt() {
                return true;
              }
            }));

    ArgumentCaptor<Context> contextArgumentCaptor =
            ArgumentCaptor.forClass(Context.class);
    verify(controller, times(1))
        .createOrUpdateResource(eq(testCustomResource), contextArgumentCaptor.capture());
    Context context = contextArgumentCaptor.getValue();
    final var retryInfo = context.getRetryInfo().get();
    assertThat(retryInfo.getAttemptCount()).isEqualTo(2);
    assertThat(retryInfo.isLastAttempt()).isEqualTo(true);
  }

  @Test
  void setReScheduleToPostExecutionControlFromUpdateControl() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(controller.createOrUpdateResource(eq(testCustomResource), any()))
        .thenReturn(
            UpdateControl.updateStatusSubResource(testCustomResource).rescheduleAfter(1000L));

    PostExecutionControl control = eventDispatcher.handleExecution(
            executionScopeWithCREvent(testCustomResource));

    assertThat(control.getReScheduleDelay().get()).isEqualTo(1000L);
  }

  @Test
  void reScheduleOnDeleteWithoutFinalizerRemoval() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);

    when(controller.deleteResource(eq(testCustomResource), any()))
            .thenReturn(
                    DeleteControl.noFinalizerRemoval().rescheduleAfter(1000L));

    PostExecutionControl control =
            eventDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(control.getReScheduleDelay().get()).isEqualTo(1000L);
  }

  @Test
  void setObservedGenerationForStatusIfNeeded() {
    var observedGenResource = createObservedGenCustomResource();

    when(configuration.isGenerationAware()).thenReturn(true);
    when(controller.createOrUpdateResource(eq(observedGenResource), any()))
        .thenReturn(
            UpdateControl.updateStatusSubResource(observedGenResource));

    when(customResourceFacade.updateStatus(observedGenResource)).thenReturn(observedGenResource);

    PostExecutionControl<ObservedGenCustomResource> control = eventDispatcher.handleExecution(
        executionScopeWithCREvent(ADDED, observedGenResource));
    assertThat(control.getUpdatedCustomResource().get().getStatus().getObservedGeneration().get())
        .isEqualTo(1L);
  }

  @Test
  private ObservedGenCustomResource createObservedGenCustomResource() {
    ObservedGenCustomResource observedGenCustomResource = new ObservedGenCustomResource();
    observedGenCustomResource.setMetadata(new ObjectMeta());
    observedGenCustomResource.getMetadata().setGeneration(1L);
    observedGenCustomResource.getMetadata().setFinalizers(new ArrayList<>());
    observedGenCustomResource.getMetadata().getFinalizers().add(DEFAULT_FINALIZER);
    return observedGenCustomResource;
  }

  private void markForDeletion(CustomResource customResource) {
    customResource.getMetadata().setDeletionTimestamp("2019-8-10");
  }

  private void removeFinalizers(CustomResource customResource) {
    customResource.getMetadata().getFinalizers().clear();
  }

  public <T extends CustomResource<?, ?>> ExecutionScope<T> executionScopeWithCREvent(T resource) {
    return new ExecutionScope<>(resource, null);
  }
}
