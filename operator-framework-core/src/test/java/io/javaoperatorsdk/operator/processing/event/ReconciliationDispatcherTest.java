package io.javaoperatorsdk.operator.processing.event;

import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.ReconciliationDispatcher.CustomResourceFacade;
import io.javaoperatorsdk.operator.sample.observedgeneration.ObservedGenCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ReconciliationDispatcherTest {

  private static final String DEFAULT_FINALIZER = "javaoperatorsdk.io/finalizer";
  public static final String ERROR_MESSAGE = "ErrorMessage";
  private TestCustomResource testCustomResource;
  private ReconciliationDispatcher<TestCustomResource> reconciliationDispatcher;
  private final Reconciler<TestCustomResource> reconciler = mock(Reconciler.class,
      withSettings().extraInterfaces(ErrorStatusHandler.class));
  private final ControllerConfiguration<TestCustomResource> configuration =
      mock(ControllerConfiguration.class);
  private final ConfigurationService configService = mock(ConfigurationService.class);
  private final CustomResourceFacade<TestCustomResource> customResourceFacade =
      mock(ReconciliationDispatcher.CustomResourceFacade.class);

  @BeforeEach
  void setup() {
    testCustomResource = TestUtils.testCustomResource();
    reconciliationDispatcher =
        init(testCustomResource, reconciler, configuration, customResourceFacade);
  }

  private <R extends HasMetadata> ReconciliationDispatcher<R> init(R customResource,
      Reconciler<R> reconciler, ControllerConfiguration<R> configuration,
      CustomResourceFacade<R> customResourceFacade) {
    when(configuration.getFinalizer()).thenReturn(DEFAULT_FINALIZER);
    when(configuration.useFinalizer()).thenCallRealMethod();
    when(configuration.getName()).thenReturn("EventDispatcherTestController");
    when(configService.getMetrics()).thenReturn(Metrics.NOOP);
    when(configuration.getConfigurationService()).thenReturn(configService);
    when(configService.getResourceCloner()).thenReturn(new Cloner() {
      @Override
      public <R extends HasMetadata> R clone(R object) {
        return object;
      }
    });
    when(reconciler.cleanup(eq(customResource), any()))
        .thenReturn(DeleteControl.defaultDelete());
    when(customResourceFacade.replaceWithLock(any())).thenReturn(null);
    Controller<R> controller =
        new Controller<>(reconciler, configuration, null);

    return new ReconciliationDispatcher<>(controller, customResourceFacade);
  }

  @Test
  void addFinalizerOnNewResource() {
    assertFalse(testCustomResource.hasFinalizer(DEFAULT_FINALIZER));
    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(reconciler, never())
        .reconcile(ArgumentMatchers.eq(testCustomResource), any());
    verify(customResourceFacade, times(1))
        .replaceWithLock(
            argThat(testCustomResource -> testCustomResource.hasFinalizer(DEFAULT_FINALIZER)));
    assertThat(testCustomResource.hasFinalizer(DEFAULT_FINALIZER));
  }

  @Test
  void callCreateOrUpdateOnNewResourceIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(reconciler, times(1))
        .reconcile(ArgumentMatchers.eq(testCustomResource), any());
  }

  @Test
  void updatesOnlyStatusSubResourceIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(reconciler.reconcile(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.updateStatus(testCustomResource));

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
    verify(customResourceFacade, never()).replaceWithLock(any());
  }

  @Test
  void updatesBothResourceAndStatusIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(reconciler.reconcile(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.updateResourceAndStatus(testCustomResource));
    when(customResourceFacade.replaceWithLock(testCustomResource)).thenReturn(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, times(1)).replaceWithLock(testCustomResource);
    verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
  }

  @Test
  void callCreateOrUpdateOnModifiedResourceIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(reconciler, times(1))
        .reconcile(ArgumentMatchers.eq(testCustomResource), any());
  }

  @Test
  void callsDeleteIfObjectHasFinalizerAndMarkedForDelete() {
    // we need to add the finalizer before marking it for deletion, as otherwise it won't get added
    assertTrue(testCustomResource.addFinalizer(DEFAULT_FINALIZER));
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(reconciler, times(1)).cleanup(eq(testCustomResource), any());
  }

  /**
   * Note that there could be more finalizers. Out of our control.
   */
  @Test
  void callDeleteOnControllerIfMarkedForDeletionWhenNoFinalizerIsConfigured() {
    configureToNotUseFinalizer();
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(reconciler).cleanup(eq(testCustomResource), any());
  }

  @Test
  void doNotCallDeleteIfMarkedForDeletionWhenFinalizerHasAlreadyBeenRemoved() {
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(reconciler, never()).cleanup(eq(testCustomResource), any());
  }

  private void configureToNotUseFinalizer() {
    ControllerConfiguration<HasMetadata> configuration =
        mock(ControllerConfiguration.class);
    when(configuration.getName()).thenReturn("EventDispatcherTestController");
    when(configService.getMetrics()).thenReturn(Metrics.NOOP);
    when(configuration.getConfigurationService()).thenReturn(configService);
    when(configuration.useFinalizer()).thenReturn(false);
    reconciliationDispatcher =
        new ReconciliationDispatcher(new Controller(reconciler, configuration, null),
            customResourceFacade);
  }

  @Test
  void doesNotAddFinalizerIfConfiguredNotTo() {
    configureToNotUseFinalizer();

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
  }

  @Test
  void removesDefaultFinalizerOnDeleteIfSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, times(1)).replaceWithLock(any());
  }

  @Test
  void doesNotRemovesTheSetFinalizerIfTheDeleteNotMethodInstructsIt() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(reconciler.cleanup(eq(testCustomResource), any()))
        .thenReturn(DeleteControl.noFinalizerRemoval());
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, never()).replaceWithLock(any());
  }

  @Test
  void doesNotUpdateTheResourceIfNoUpdateUpdateControlIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(reconciler.reconcile(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.noUpdate());

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(customResourceFacade, never()).replaceWithLock(any());
    verify(customResourceFacade, never()).updateStatus(testCustomResource);
  }

  @Test
  void addsFinalizerIfNotMarkedForDeletionAndEmptyCustomResourceReturned() {
    removeFinalizers(testCustomResource);
    when(reconciler.reconcile(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.noUpdate());

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, times(1)).replaceWithLock(any());
  }

  @Test
  void doesNotCallDeleteIfMarkedForDeletionButNotOurFinalizer() {
    removeFinalizers(testCustomResource);
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, never()).replaceWithLock(any());
    verify(reconciler, never()).cleanup(eq(testCustomResource), any());
  }

  @Test
  void executeControllerRegardlessGenerationInNonGenerationAwareModeIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(reconciler, times(2)).reconcile(eq(testCustomResource), any());
  }

  @Test
  void propagatesRetryInfoToContextIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciliationDispatcher.handleExecution(
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
    verify(reconciler, times(1))
        .reconcile(any(), contextArgumentCaptor.capture());
    Context context = contextArgumentCaptor.getValue();
    final var retryInfo = context.getRetryInfo().get();
    assertThat(retryInfo.getAttemptCount()).isEqualTo(2);
    assertThat(retryInfo.isLastAttempt()).isEqualTo(true);
  }

  @Test
  void setReScheduleToPostExecutionControlFromUpdateControl() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(reconciler.reconcile(eq(testCustomResource), any()))
        .thenReturn(
            UpdateControl.updateStatus(testCustomResource).rescheduleAfter(1000L));

    PostExecutionControl control =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(control.getReScheduleDelay().get()).isEqualTo(1000L);
  }

  @Test
  void reScheduleOnDeleteWithoutFinalizerRemoval() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);

    when(reconciler.cleanup(eq(testCustomResource), any()))
        .thenReturn(DeleteControl.noFinalizerRemoval().rescheduleAfter(1000L));

    PostExecutionControl control =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(control.getReScheduleDelay().get()).isEqualTo(1000L);
  }

  @Test
  void setObservedGenerationForStatusIfNeeded() {
    var observedGenResource = createObservedGenCustomResource();

    Reconciler<ObservedGenCustomResource> reconciler = mock(Reconciler.class);
    ControllerConfiguration<ObservedGenCustomResource> config =
        mock(ControllerConfiguration.class);
    CustomResourceFacade<ObservedGenCustomResource> facade = mock(CustomResourceFacade.class);
    var dispatcher = init(observedGenResource, reconciler, config, facade);

    when(config.isGenerationAware()).thenReturn(true);
    when(reconciler.reconcile(any(), any()))
        .thenReturn(UpdateControl.updateStatus(observedGenResource));
    when(facade.updateStatus(observedGenResource)).thenReturn(observedGenResource);

    PostExecutionControl<ObservedGenCustomResource> control = dispatcher.handleExecution(
        executionScopeWithCREvent(observedGenResource));
    assertThat(control.getUpdatedCustomResource().get().getStatus().getObservedGeneration())
        .isEqualTo(1L);
  }

  @Test
  void updatesObservedGenerationOnNoUpdateUpdateControl() {
    var observedGenResource = createObservedGenCustomResource();

    Reconciler<ObservedGenCustomResource> reconciler = mock(Reconciler.class);
    ControllerConfiguration<ObservedGenCustomResource> config =
        mock(ControllerConfiguration.class);
    CustomResourceFacade<ObservedGenCustomResource> facade = mock(CustomResourceFacade.class);
    when(config.isGenerationAware()).thenReturn(true);
    when(reconciler.reconcile(any(), any()))
        .thenReturn(UpdateControl.noUpdate());
    when(facade.updateStatus(observedGenResource)).thenReturn(observedGenResource);
    var dispatcher = init(observedGenResource, reconciler, config, facade);

    PostExecutionControl<ObservedGenCustomResource> control = dispatcher.handleExecution(
        executionScopeWithCREvent(observedGenResource));
    assertThat(control.getUpdatedCustomResource().get().getStatus().getObservedGeneration())
        .isEqualTo(1L);
  }

  @Test
  void callErrorStatusHandlerIfImplemented() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(reconciler.reconcile(any(), any()))
        .thenThrow(new IllegalStateException("Error Status Test"));
    when(((ErrorStatusHandler) reconciler).updateErrorStatus(any(), any(), any())).then(a -> {
      testCustomResource.getStatus().setConfigMapStatus(ERROR_MESSAGE);
      return Optional.of(testCustomResource);
    });

    reconciliationDispatcher.handleExecution(
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

    verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
    verify(((ErrorStatusHandler) reconciler), times(1)).updateErrorStatus(eq(testCustomResource),
        any(), any());
  }

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

  public <T extends HasMetadata> ExecutionScope<T> executionScopeWithCREvent(T resource) {
    return new ExecutionScope<>(resource, null);
  }
}
