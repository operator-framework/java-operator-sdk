package io.javaoperatorsdk.operator.processing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import io.javaoperatorsdk.operator.Metrics;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.RetryInfo;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
  private CustomResource testCustomResource;
  private EventDispatcher eventDispatcher;
  private final ResourceController<CustomResource> controller = mock(ResourceController.class);
  private final ControllerConfiguration<CustomResource> configuration =
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
        .thenReturn(DeleteControl.DEFAULT_DELETE);
    when(customResourceFacade.replaceWithLock(any())).thenReturn(null);
  }

  @Test
  void addFinalizerOnNewResource() {
    assertFalse(testCustomResource.hasFinalizer(DEFAULT_FINALIZER));
    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.ADDED, testCustomResource));
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
        executionScopeWithCREvent(Watcher.Action.ADDED, testCustomResource));
    verify(controller, times(1))
        .createOrUpdateResource(ArgumentMatchers.eq(testCustomResource), any());
  }

  @Test
  void updatesOnlyStatusSubResourceIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(controller.createOrUpdateResource(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.updateStatusSubResource(testCustomResource));

    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.ADDED, testCustomResource));

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
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));

    verify(customResourceFacade, times(1)).replaceWithLock(testCustomResource);
    verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
  }

  @Test
  void callCreateOrUpdateOnModifiedResourceIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));
    verify(controller, times(1))
        .createOrUpdateResource(ArgumentMatchers.eq(testCustomResource), any());
  }

  @Test
  void callsDeleteIfObjectHasFinalizerAndMarkedForDelete() {
    // we need to add the finalizer before marking it for deletion, as otherwise it won't get added
    assertTrue(testCustomResource.addFinalizer(DEFAULT_FINALIZER));
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));

    verify(controller, times(1)).deleteResource(eq(testCustomResource), any());
  }

  /** Note that there could be more finalizers. Out of our control. */
  @Test
  void callDeleteOnControllerIfMarkedForDeletionWhenNoFinalizerIsConfigured() {
    configureToNotUseFinalizer();
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));

    verify(controller).deleteResource(eq(testCustomResource), any());
  }

  @Test
  void doNotCallDeleteIfMarkedForDeletionWhenFinalizerHasAlreadyBeenRemoved() {
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));

    verify(controller, never()).deleteResource(eq(testCustomResource), any());
  }

  private void configureToNotUseFinalizer() {
    ControllerConfiguration<CustomResource> configuration = mock(ControllerConfiguration.class);
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
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));

    assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
  }

  @Test
  void removesDefaultFinalizerOnDeleteIfSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));

    assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, times(1)).replaceWithLock(any());
  }

  @Test
  void doesNotRemovesTheSetFinalizerIfTheDeleteNotMethodInstructsIt() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(controller.deleteResource(eq(testCustomResource), any()))
        .thenReturn(DeleteControl.NO_FINALIZER_REMOVAL);
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));

    assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, never()).replaceWithLock(any());
  }

  @Test
  void doesNotUpdateTheResourceIfNoUpdateUpdateControlIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(controller.createOrUpdateResource(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.noUpdate());

    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));
    verify(customResourceFacade, never()).replaceWithLock(any());
    verify(customResourceFacade, never()).updateStatus(testCustomResource);
  }

  @Test
  void addsFinalizerIfNotMarkedForDeletionAndEmptyCustomResourceReturned() {
    removeFinalizers(testCustomResource);
    when(controller.createOrUpdateResource(eq(testCustomResource), any()))
        .thenReturn(UpdateControl.noUpdate());

    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));

    assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, times(1)).replaceWithLock(any());
  }

  @Test
  void doesNotCallDeleteIfMarkedForDeletionButNotOurFinalizer() {
    removeFinalizers(testCustomResource);
    markForDeletion(testCustomResource);

    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));

    verify(customResourceFacade, never()).replaceWithLock(any());
    verify(controller, never()).deleteResource(eq(testCustomResource), any());
  }

  @Test
  void executeControllerRegardlessGenerationInNonGenerationAwareModeIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));
    eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.MODIFIED, testCustomResource));

    verify(controller, times(2)).createOrUpdateResource(eq(testCustomResource), any());
  }

  @Test
  void propagatesRetryInfoToContextIfFinalizerSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    eventDispatcher.handleExecution(
        new ExecutionScope(
            Arrays.asList(),
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

    ArgumentCaptor<Context<CustomResource>> contextArgumentCaptor =
        ArgumentCaptor.forClass(Context.class);
    verify(controller, times(1))
        .createOrUpdateResource(eq(testCustomResource), contextArgumentCaptor.capture());
    Context<CustomResource> context = contextArgumentCaptor.getValue();
    final var retryInfo = context.getRetryInfo().get();
    assertThat(retryInfo.getAttemptCount()).isEqualTo(2);
    assertThat(retryInfo.isLastAttempt()).isEqualTo(true);
  }

  @Test
  void setReScheduleToPostExecutionControlFromUpdateControl() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    when(controller.createOrUpdateResource(eq(testCustomResource), any()))
        .thenReturn(
            UpdateControl.updateStatusSubResource(testCustomResource).withReSchedule(1000l));

    PostExecutionControl control = eventDispatcher.handleExecution(
        executionScopeWithCREvent(Watcher.Action.ADDED, testCustomResource));

    assertThat(control.getReScheduleDelay().get()).isEqualTo(1000l);
  }

  private void markForDeletion(CustomResource customResource) {
    customResource.getMetadata().setDeletionTimestamp("2019-8-10");
  }

  private void removeFinalizers(CustomResource customResource) {
    customResource.getMetadata().getFinalizers().clear();
  }

  public ExecutionScope executionScopeWithCREvent(
      Watcher.Action action, CustomResource resource, Event... otherEvents) {
    CustomResourceEvent event = new CustomResourceEvent(action, resource, null);
    List<Event> eventList = new ArrayList<>(1 + otherEvents.length);
    eventList.add(event);
    eventList.addAll(Arrays.asList(otherEvents));
    return new ExecutionScope(eventList, resource, null);
  }
}
