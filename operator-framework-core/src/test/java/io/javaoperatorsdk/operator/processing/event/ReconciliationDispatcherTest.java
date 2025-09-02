package io.javaoperatorsdk.operator.processing.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.stubbing.Answer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.ReconciliationDispatcher.CustomResourceFacade;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.sample.observedgeneration.ObservedGenCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.TestUtils.markForDeletion;
import static io.javaoperatorsdk.operator.processing.event.ReconciliationDispatcher.MAX_UPDATE_RETRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class ReconciliationDispatcherTest {

  private static final String DEFAULT_FINALIZER = "javaoperatorsdk.io/finalizer";
  public static final String ERROR_MESSAGE = "ErrorMessage";
  public static final long RECONCILIATION_MAX_INTERVAL = 10L;
  private TestCustomResource testCustomResource;
  private ReconciliationDispatcher<TestCustomResource> reconciliationDispatcher;
  private TestReconciler reconciler;
  private final CustomResourceFacade<TestCustomResource> customResourceFacade =
      mock(ReconciliationDispatcher.CustomResourceFacade.class);
  private static ConfigurationService configurationService;

  @BeforeEach
  void setup() {
    initConfigService(true);
    testCustomResource = TestUtils.testCustomResource();
    reconciler = spy(new TestReconciler());
    reconciliationDispatcher =
        init(testCustomResource, reconciler, null, customResourceFacade, true);
  }

  static void initConfigService(boolean useSSA) {
    initConfigService(useSSA, true);
  }

  static void initConfigService(boolean useSSA, boolean noCloning) {
    /*
     * We need this for mock reconcilers to properly generate the expected UpdateControl: without
     * this, calls such as `when(reconciler.reconcile(eq(testCustomResource),
     * any())).thenReturn(UpdateControl.updateStatus(testCustomResource))` will return null because
     * equals will fail on the two equal but NOT identical TestCustomResources because equals is not
     * implemented on TestCustomResourceSpec or TestCustomResourceStatus
     */
    configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(),
            overrider ->
                overrider
                    .checkingCRDAndValidateLocalModel(false)
                    .withResourceCloner(
                        new Cloner() {
                          @Override
                          public <R extends HasMetadata> R clone(R object) {
                            if (noCloning) {
                              return object;
                            } else {
                              return new KubernetesSerialization().clone(object);
                            }
                          }
                        })
                    .withUseSSAToPatchPrimaryResource(useSSA));
  }

  private <R extends HasMetadata> ReconciliationDispatcher<R> init(
      R customResource,
      Reconciler<R> reconciler,
      ControllerConfiguration<R> configuration,
      CustomResourceFacade<R> customResourceFacade,
      boolean useFinalizer) {

    final Class<R> resourceClass = (Class<R>) customResource.getClass();
    configuration =
        configuration == null
            ? MockControllerConfiguration.forResource(resourceClass, configurationService)
            : configuration;

    when(configuration.getConfigurationService()).thenReturn(configurationService);
    when(configuration.getFinalizerName()).thenReturn(DEFAULT_FINALIZER);
    when(configuration.getName()).thenReturn("EventDispatcherTestController");
    when(configuration.getResourceClass()).thenReturn(resourceClass);
    // needed so the retry can be predefined
    if (configuration.getRetry() == null) {
      when(configuration.getRetry()).thenReturn(new GenericRetry());
    }
    when(configuration.maxReconciliationInterval())
        .thenReturn(Optional.of(Duration.ofHours(RECONCILIATION_MAX_INTERVAL)));

    Controller<R> controller =
        new Controller<>(reconciler, configuration, MockKubernetesClient.client(resourceClass)) {
          @Override
          public boolean useFinalizer() {
            return useFinalizer;
          }
        };
    controller.start();

    return new ReconciliationDispatcher<>(controller, customResourceFacade);
  }

  @Test
  void addFinalizerOnNewResource() {
    assertFalse(testCustomResource.hasFinalizer(DEFAULT_FINALIZER));
    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(reconciler, never()).reconcile(ArgumentMatchers.eq(testCustomResource), any());
    verify(customResourceFacade, times(1))
        .patchResourceWithSSA(
            argThat(testCustomResource -> testCustomResource.hasFinalizer(DEFAULT_FINALIZER)));
  }

  @Test
  void addFinalizerOnNewResourceWithoutSSA() {
    initConfigService(false);
    final ReconciliationDispatcher<TestCustomResource> dispatcher =
        init(testCustomResource, reconciler, null, customResourceFacade, true);

    assertFalse(testCustomResource.hasFinalizer(DEFAULT_FINALIZER));
    dispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(reconciler, never()).reconcile(ArgumentMatchers.eq(testCustomResource), any());
    verify(customResourceFacade, times(1))
        .patchResource(
            argThat(testCustomResource -> testCustomResource.hasFinalizer(DEFAULT_FINALIZER)),
            any());
    assertThat(testCustomResource.hasFinalizer(DEFAULT_FINALIZER)).isTrue();
  }

  @Test
  void callCreateOrUpdateOnNewResourceIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(reconciler, times(1)).reconcile(ArgumentMatchers.eq(testCustomResource), any());
  }

  @Test
  void patchesBothResourceAndStatusIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile = (r, c) -> UpdateControl.patchResourceAndStatus(testCustomResource);
    when(customResourceFacade.patchResource(eq(testCustomResource), any()))
        .thenReturn(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, times(1)).patchResource(eq(testCustomResource), any());
    verify(customResourceFacade, times(1)).patchStatus(eq(testCustomResource), any());
  }

  @Test
  void patchesStatus() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile = (r, c) -> UpdateControl.patchStatus(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, times(1)).patchStatus(eq(testCustomResource), any());
    verify(customResourceFacade, never()).patchResource(any(), any());
  }

  @Test
  void callCreateOrUpdateOnModifiedResourceIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(reconciler, times(1)).reconcile(ArgumentMatchers.eq(testCustomResource), any());
  }

  @Test
  void callsDeleteIfObjectHasFinalizerAndMarkedForDelete() {
    // we need to add the finalizer before marking it for deletion, as otherwise it won't get added
    assertTrue(testCustomResource.addFinalizer(DEFAULT_FINALIZER));
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(reconciler, times(1)).cleanup(eq(testCustomResource), any());
  }

  @Test
  void removesDefaultFinalizerOnDeleteIfSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);

    var postExecControl =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(postExecControl.isFinalizerRemoved()).isTrue();
    verify(customResourceFacade, times(1)).patchResourceWithoutSSA(eq(testCustomResource), any());
  }

  @Test
  void retriesFinalizerRemovalWithFreshResource() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);
    var resourceWithFinalizer = TestUtils.testCustomResource();
    resourceWithFinalizer.addFinalizer(DEFAULT_FINALIZER);
    when(customResourceFacade.patchResourceWithoutSSA(eq(testCustomResource), any()))
        .thenThrow(new KubernetesClientException(null, 409, null))
        .thenReturn(testCustomResource);
    when(customResourceFacade.getResource(any(), any())).thenReturn(resourceWithFinalizer);

    var postExecControl =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(postExecControl.isFinalizerRemoved()).isTrue();
    verify(customResourceFacade, times(2)).patchResourceWithoutSSA(any(), any());
    verify(customResourceFacade, times(1)).getResource(any(), any());
  }

  @Test
  void nullResourceIsGracefullyHandledOnFinalizerRemovalRetry() {
    // simulate the operator not able or not be allowed to get the custom resource during the retry
    // of the finalizer removal
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);
    when(customResourceFacade.patchResourceWithoutSSA(any(), any()))
        .thenThrow(new KubernetesClientException(null, 409, null));
    when(customResourceFacade.getResource(any(), any())).thenReturn(null);

    var postExecControl =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(postExecControl.isFinalizerRemoved()).isTrue();
    verify(customResourceFacade, times(1)).patchResourceWithoutSSA(eq(testCustomResource), any());
    verify(customResourceFacade, times(1)).getResource(any(), any());
  }

  @Test
  void throwsExceptionIfFinalizerRemovalRetryExceeded() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);
    when(customResourceFacade.patchResourceWithoutSSA(any(), any()))
        .thenThrow(new KubernetesClientException(null, 409, null));
    when(customResourceFacade.getResource(any(), any()))
        .thenAnswer((Answer<TestCustomResource>) invocationOnMock -> createResourceWithFinalizer());

    var postExecControl =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(postExecControl.isFinalizerRemoved()).isFalse();
    assertThat(postExecControl.getRuntimeException()).isPresent();
    assertThat(postExecControl.getRuntimeException().get()).isInstanceOf(OperatorException.class);
    verify(customResourceFacade, times(MAX_UPDATE_RETRY)).patchResourceWithoutSSA(any(), any());
    verify(customResourceFacade, times(MAX_UPDATE_RETRY - 1)).getResource(any(), any());
  }

  @Test
  void throwsExceptionIfFinalizerRemovalClientExceptionIsNotConflict() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);
    when(customResourceFacade.patchResourceWithoutSSA(any(), any()))
        .thenThrow(new KubernetesClientException(null, 400, null));

    var res =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(res.getRuntimeException()).isPresent();
    assertThat(res.getRuntimeException().get()).isInstanceOf(KubernetesClientException.class);
    verify(customResourceFacade, times(1)).patchResourceWithoutSSA(any(), any());
    verify(customResourceFacade, never()).getResource(any(), any());
  }

  @Test
  void doesNotCallDeleteOnControllerIfMarkedForDeletionWhenNoFinalizerIsConfigured() {
    final ReconciliationDispatcher<TestCustomResource> dispatcher =
        init(testCustomResource, reconciler, null, customResourceFacade, false);
    markForDeletion(testCustomResource);

    dispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(reconciler, times(0)).cleanup(eq(testCustomResource), any());
  }

  @Test
  void doNotCallDeleteIfMarkedForDeletionWhenFinalizerHasAlreadyBeenRemoved() {
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(reconciler, never()).cleanup(eq(testCustomResource), any());
  }

  @Test
  void doesNotAddFinalizerIfConfiguredNotTo() {
    final ReconciliationDispatcher<TestCustomResource> dispatcher =
        init(testCustomResource, reconciler, null, customResourceFacade, false);

    dispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
  }

  @Test
  void doesNotRemovesTheSetFinalizerIfTheDeleteNotMethodInstructsIt() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.cleanup = (r, c) -> DeleteControl.noFinalizerRemoval();
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, never()).patchResource(any(), any());
  }

  @Test
  void doesNotUpdateTheResourceIfNoUpdateUpdateControlIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile = (r, c) -> UpdateControl.noUpdate();

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(customResourceFacade, never()).patchResource(any(), any());
    verify(customResourceFacade, never()).patchStatus(eq(testCustomResource), any());
  }

  @Test
  void addsFinalizerIfNotMarkedForDeletionAndEmptyCustomResourceReturned() {
    removeFinalizers(testCustomResource);
    reconciler.reconcile = (r, c) -> UpdateControl.noUpdate();
    when(customResourceFacade.patchResourceWithSSA(any())).thenReturn(testCustomResource);

    var postExecControl =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, times(1))
        .patchResourceWithSSA(argThat(a -> !a.getMetadata().getFinalizers().isEmpty()));
    assertThat(postExecControl.updateIsStatusPatch()).isFalse();
    assertThat(postExecControl.getUpdatedCustomResource()).isPresent();
  }

  @Test
  void doesNotCallDeleteIfMarkedForDeletionButNotOurFinalizer() {
    removeFinalizers(testCustomResource);
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, never()).patchResource(any(), any());
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
                new RetryInfo() {
                  @Override
                  public int getAttemptCount() {
                    return 2;
                  }

                  @Override
                  public boolean isLastAttempt() {
                    return true;
                  }
                })
            .setResource(testCustomResource));

    ArgumentCaptor<Context> contextArgumentCaptor = ArgumentCaptor.forClass(Context.class);
    verify(reconciler, times(1)).reconcile(any(), contextArgumentCaptor.capture());
    Context<?> context = contextArgumentCaptor.getValue();
    final var retryInfo = context.getRetryInfo().orElseGet(() -> fail("Missing optional"));
    assertThat(retryInfo.getAttemptCount()).isEqualTo(2);
    assertThat(retryInfo.isLastAttempt()).isEqualTo(true);
  }

  @Test
  void setReScheduleToPostExecutionControlFromUpdateControl() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile =
        (r, c) -> UpdateControl.patchStatus(testCustomResource).rescheduleAfter(1000L);

    PostExecutionControl control =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(control.getReScheduleDelay().orElseGet(() -> fail("Missing optional")))
        .isEqualTo(1000L);
  }

  @Test
  void reScheduleOnDeleteWithoutFinalizerRemoval() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);

    reconciler.cleanup =
        (r, c) -> DeleteControl.noFinalizerRemoval().rescheduleAfter(1, TimeUnit.SECONDS);

    PostExecutionControl control =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(control.getReScheduleDelay().orElseGet(() -> fail("Missing optional")))
        .isEqualTo(1000L);
  }

  @Test
  void doesNotUpdatesObservedGenerationIfStatusIsNotPatchedWhenUsingSSA() throws Exception {
    var observedGenResource = createObservedGenCustomResource();

    Reconciler<ObservedGenCustomResource> reconciler = mock(Reconciler.class);
    final var config = MockControllerConfiguration.forResource(ObservedGenCustomResource.class);
    CustomResourceFacade<ObservedGenCustomResource> facade = mock(CustomResourceFacade.class);
    when(config.isGenerationAware()).thenReturn(true);
    when(reconciler.reconcile(any(), any())).thenReturn(UpdateControl.noUpdate());
    when(facade.patchStatus(any(), any())).thenReturn(observedGenResource);
    var dispatcher = init(observedGenResource, reconciler, config, facade, true);

    PostExecutionControl<ObservedGenCustomResource> control =
        dispatcher.handleExecution(executionScopeWithCREvent(observedGenResource));
    assertThat(control.getUpdatedCustomResource()).isEmpty();
  }

  @Test
  void doesNotPatchObservedGenerationOnCustomResourcePatch() throws Exception {
    var observedGenResource = createObservedGenCustomResource();

    Reconciler<ObservedGenCustomResource> reconciler = mock(Reconciler.class);
    final var config = MockControllerConfiguration.forResource(ObservedGenCustomResource.class);
    CustomResourceFacade<ObservedGenCustomResource> facade = mock(CustomResourceFacade.class);
    when(config.isGenerationAware()).thenReturn(true);
    when(reconciler.reconcile(any(), any()))
        .thenReturn(UpdateControl.patchResource(observedGenResource));
    when(facade.patchResource(any(), any())).thenReturn(observedGenResource);
    var dispatcher = init(observedGenResource, reconciler, config, facade, false);

    dispatcher.handleExecution(executionScopeWithCREvent(observedGenResource));

    verify(facade, never()).patchStatus(any(), any());
  }

  @Test
  void callErrorStatusHandlerIfImplemented() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile =
        (r, c) -> {
          throw new IllegalStateException("Error Status Test");
        };
    reconciler.errorHandler =
        () -> {
          testCustomResource.getStatus().setConfigMapStatus(ERROR_MESSAGE);
          return ErrorStatusUpdateControl.patchStatus(testCustomResource);
        };

    reconciliationDispatcher.handleExecution(
        new ExecutionScope(
                new RetryInfo() {
                  @Override
                  public int getAttemptCount() {
                    return 2;
                  }

                  @Override
                  public boolean isLastAttempt() {
                    return true;
                  }
                })
            .setResource(testCustomResource));

    verify(customResourceFacade, times(1)).patchStatus(eq(testCustomResource), any());
    verify(reconciler, times(1)).updateErrorStatus(eq(testCustomResource), any(), any());
  }

  @Test
  void callErrorStatusHandlerEvenOnFirstError() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile =
        (r, c) -> {
          throw new IllegalStateException("Error Status Test");
        };
    reconciler.errorHandler =
        () -> {
          testCustomResource.getStatus().setConfigMapStatus(ERROR_MESSAGE);
          return ErrorStatusUpdateControl.patchStatus(testCustomResource);
        };

    var postExecControl =
        reconciliationDispatcher.handleExecution(
            new ExecutionScope(null).setResource(testCustomResource));
    verify(customResourceFacade, times(1)).patchStatus(eq(testCustomResource), any());
    verify(reconciler, times(1)).updateErrorStatus(eq(testCustomResource), any(), any());
    assertThat(postExecControl.exceptionDuringExecution()).isTrue();
  }

  @Test
  void errorHandlerCanInstructNoRetryWithUpdate() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    reconciler.reconcile =
        (r, c) -> {
          throw new IllegalStateException("Error Status Test");
        };
    reconciler.errorHandler =
        () -> {
          testCustomResource.getStatus().setConfigMapStatus(ERROR_MESSAGE);
          return ErrorStatusUpdateControl.patchStatus(testCustomResource).withNoRetry();
        };

    var postExecControl =
        reconciliationDispatcher.handleExecution(
            new ExecutionScope(null).setResource(testCustomResource));

    verify(reconciler, times(1)).updateErrorStatus(eq(testCustomResource), any(), any());
    verify(customResourceFacade, times(1)).patchStatus(eq(testCustomResource), any());
    assertThat(postExecControl.exceptionDuringExecution()).isFalse();
  }

  @Test
  void errorHandlerCanInstructNoRetryNoUpdate() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    reconciler.reconcile =
        (r, c) -> {
          throw new IllegalStateException("Error Status Test");
        };
    reconciler.errorHandler =
        () -> {
          testCustomResource.getStatus().setConfigMapStatus(ERROR_MESSAGE);
          return ErrorStatusUpdateControl.<TestCustomResource>noStatusUpdate().withNoRetry();
        };

    var postExecControl =
        reconciliationDispatcher.handleExecution(
            new ExecutionScope(null).setResource(testCustomResource));

    verify(reconciler, times(1)).updateErrorStatus(eq(testCustomResource), any(), any());
    verify(customResourceFacade, times(0)).patchStatus(eq(testCustomResource), any());
    assertThat(postExecControl.exceptionDuringExecution()).isFalse();
  }

  @Test
  void errorStatusHandlerCanPatchResource() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    reconciler.reconcile =
        (r, c) -> {
          throw new IllegalStateException("Error Status Test");
        };
    reconciler.errorHandler = () -> ErrorStatusUpdateControl.patchStatus(testCustomResource);

    reconciliationDispatcher.handleExecution(
        new ExecutionScope(null).setResource(testCustomResource));

    verify(customResourceFacade, times(1)).patchStatus(eq(testCustomResource), any());
    verify(reconciler, times(1)).updateErrorStatus(eq(testCustomResource), any(), any());
  }

  @Test
  void ifRetryLimitedToZeroMaxAttemptsErrorHandlerGetsCorrectLastAttempt() {
    var configuration =
        MockControllerConfiguration.forResource(
            (Class<TestCustomResource>) testCustomResource.getClass());
    when(configuration.getRetry()).thenReturn(new GenericRetry().setMaxAttempts(0));
    reconciliationDispatcher =
        init(testCustomResource, reconciler, configuration, customResourceFacade, false);

    reconciler.reconcile =
        (r, c) -> {
          throw new IllegalStateException("Error Status Test");
        };

    reconciler.errorHandler = () -> ErrorStatusUpdateControl.noStatusUpdate();

    reconciliationDispatcher.handleExecution(
        new ExecutionScope(null).setResource(testCustomResource));

    verify(reconciler, times(1))
        .updateErrorStatus(
            any(),
            ArgumentMatchers.argThat(
                context -> {
                  var retryInfo = context.getRetryInfo().orElseThrow();
                  return retryInfo.isLastAttempt();
                }),
            any());
  }

  @Test
  void canSkipSchedulingMaxDelayIf() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile = (r, c) -> UpdateControl.noUpdate();
    when(reconciliationDispatcher.configuration().maxReconciliationInterval())
        .thenReturn(Optional.empty());

    PostExecutionControl control =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(control.getReScheduleDelay()).isNotPresent();
  }

  @Test
  void retriesAddingFinalizerWithoutSSA() {
    initConfigService(false);
    reconciliationDispatcher =
        init(testCustomResource, reconciler, null, customResourceFacade, true);

    removeFinalizers(testCustomResource);
    reconciler.reconcile = (r, c) -> UpdateControl.noUpdate();
    when(customResourceFacade.patchResource(any(), any()))
        .thenThrow(new KubernetesClientException(null, 409, null))
        .thenReturn(testCustomResource);
    when(customResourceFacade.getResource(any(), any()))
        .then(
            (Answer<TestCustomResource>)
                invocationOnMock -> {
                  testCustomResource.getFinalizers().clear();
                  return testCustomResource;
                });

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, times(2)).patchResource(any(), any());
  }

  @Test
  void reSchedulesFromErrorHandler() {
    var delay = 1000L;
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    reconciler.reconcile =
        (r, c) -> {
          throw new IllegalStateException("Error Status Test");
        };
    reconciler.errorHandler =
        () -> ErrorStatusUpdateControl.<TestCustomResource>noStatusUpdate().rescheduleAfter(delay);

    var res =
        reconciliationDispatcher.handleExecution(
            new ExecutionScope(null).setResource(testCustomResource));

    assertThat(res.getReScheduleDelay()).contains(delay);
    assertThat(res.getRuntimeException()).isEmpty();
  }

  @Test
  void reconcilerContextUsesTheSameInstanceOfResourceAsParam() {
    initConfigService(false, false);

    final ReconciliationDispatcher<TestCustomResource> dispatcher =
        init(testCustomResource, reconciler, null, customResourceFacade, true);

    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    ArgumentCaptor<DefaultContext> contextArgumentCaptor =
        ArgumentCaptor.forClass(DefaultContext.class);
    ArgumentCaptor<TestCustomResource> customResourceCaptor =
        ArgumentCaptor.forClass(TestCustomResource.class);

    dispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(reconciler, times(1))
        .reconcile(customResourceCaptor.capture(), contextArgumentCaptor.capture());

    assertThat(contextArgumentCaptor.getValue().getPrimaryResource())
        .isSameAs(customResourceCaptor.getValue())
        .isNotSameAs(testCustomResource);
  }

  @Test
  void allEventModeNoReSchedulesAllowedForDeleteEvent() {}

  @Test
  void allEventModeCallsCleanupOnDeleteEvent() {}

  @Test
  void allEventModeCallsCleanupOnMarkedForDeletion() {}

  private ObservedGenCustomResource createObservedGenCustomResource() {
    ObservedGenCustomResource observedGenCustomResource = new ObservedGenCustomResource();
    observedGenCustomResource.setMetadata(new ObjectMeta());
    observedGenCustomResource.getMetadata().setGeneration(1L);
    observedGenCustomResource.getMetadata().setFinalizers(new ArrayList<>());
    observedGenCustomResource.getMetadata().getFinalizers().add(DEFAULT_FINALIZER);
    return observedGenCustomResource;
  }

  TestCustomResource createResourceWithFinalizer() {
    var resourceWithFinalizer = TestUtils.testCustomResource();
    resourceWithFinalizer.addFinalizer(DEFAULT_FINALIZER);
    return resourceWithFinalizer;
  }

  private void removeFinalizers(CustomResource customResource) {
    customResource.getMetadata().getFinalizers().clear();
  }

  public <T extends HasMetadata> ExecutionScope<T> executionScopeWithCREvent(T resource) {
    return (ExecutionScope<T>) new ExecutionScope<>(null).setResource(resource);
  }

  private class TestReconciler
      implements Reconciler<TestCustomResource>, Cleaner<TestCustomResource> {

    private BiFunction<TestCustomResource, Context, UpdateControl<TestCustomResource>> reconcile;
    private BiFunction<TestCustomResource, Context, DeleteControl> cleanup;
    private Supplier<ErrorStatusUpdateControl> errorHandler;

    @Override
    public UpdateControl<TestCustomResource> reconcile(
        TestCustomResource resource, Context context) {
      if (reconcile != null && resource.equals(testCustomResource)) {
        return reconcile.apply(resource, context);
      }
      return UpdateControl.noUpdate();
    }

    @Override
    public DeleteControl cleanup(TestCustomResource resource, Context context) {
      if (cleanup != null && resource.equals(testCustomResource)) {
        return cleanup.apply(resource, context);
      }
      return DeleteControl.defaultDelete();
    }

    @Override
    public ErrorStatusUpdateControl<TestCustomResource> updateErrorStatus(
        TestCustomResource resource, Context<TestCustomResource> context, Exception e) {
      return errorHandler != null ? errorHandler.get() : ErrorStatusUpdateControl.noStatusUpdate();
    }
  }
}
