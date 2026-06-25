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
package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.ResolvedControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSourceTestBase;
import io.javaoperatorsdk.operator.processing.event.source.EventFilterTestUtils;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.event.source.EventFilterTestUtils.withResourceVersion;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ControllerEventSourceTest
    extends AbstractEventSourceTestBase<ControllerEventSource<TestCustomResource>, EventHandler> {

  public static final String FINALIZER =
      ReconcilerUtilsInternal.getDefaultFinalizerName(TestCustomResource.class);

  private final TestController testController = new TestController(true);
  private final ControllerConfiguration controllerConfig = mock(ControllerConfiguration.class);

  @BeforeEach
  public void setup() {
    when(controllerConfig.getConfigurationService()).thenReturn(new BaseConfigurationService());
    var ic = mock(InformerConfiguration.class);
    when(controllerConfig.getInformerConfig()).thenReturn(ic);

    setUpSource(new ControllerEventSource<>(testController), true, controllerConfig);
  }

  @Test
  void skipsEventHandlingIfGenerationNotIncreased() {
    TestCustomResource customResource = TestUtils.testCustomResource();
    customResource.getMetadata().setFinalizers(List.of(FINALIZER));
    customResource.getMetadata().setGeneration(2L);

    TestCustomResource oldCustomResource = TestUtils.testCustomResource();
    oldCustomResource.getMetadata().setFinalizers(List.of(FINALIZER));

    source.handleEvent(ResourceAction.UPDATED, customResource, oldCustomResource, null, null);
    verify(eventHandler, times(1)).handleEvent(any());

    source.handleEvent(ResourceAction.UPDATED, customResource, customResource, null, null);
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  void dontSkipEventHandlingIfMarkedForDeletion() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, null);
    verify(eventHandler, times(1)).handleEvent(any());

    customResource1.getMetadata().setDeletionTimestamp(LocalDateTime.now().toString());
    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, null);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void normalExecutionIfGenerationChanges() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, null);
    verify(eventHandler, times(1)).handleEvent(any());

    customResource1.getMetadata().setGeneration(2L);
    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, null);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void handlesAllEventIfNotGenerationAware() {
    source = new ControllerEventSource<>(new TestController(false));
    setup();

    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, null);
    verify(eventHandler, times(1)).handleEvent(any());

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, null);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void eventWithNoGenerationProcessedIfNoFinalizer() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, null);

    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  void callsBroadcastsOnResourceEvents() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, null);

    verify(testController.getEventSourceManager(), times(1))
        .broadcastOnResourceEvent(
            eq(ResourceAction.UPDATED), eq(customResource1), eq(customResource1));
  }

  @Test
  void withoutDefaultFiltersUserFilterIsAppliedDirectly() {
    TestCustomResource cr = TestUtils.testCustomResource();
    cr.getMetadata().setFinalizers(List.of(FINALIZER));
    cr.getMetadata().setGeneration(1L);

    // Without default filters, only the user filter runs — no internal generation/finalizer checks.
    // User filter accepts unconditionally, so the event passes even with same generation.
    OnUpdateFilter<TestCustomResource> userFilter = (newRes, oldRes) -> true;
    source = new ControllerEventSource<>(new TestController(null, userFilter, null, false));
    setUpSource(source, true, controllerConfig);

    source.handleEvent(ResourceAction.UPDATED, cr, cr, null);

    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  void withoutDefaultFiltersUserFilterCanRejectEvents() {
    TestCustomResource cr = TestUtils.testCustomResource();

    OnUpdateFilter<TestCustomResource> userFilter = (newRes, oldRes) -> false;
    source = new ControllerEventSource<>(new TestController(null, userFilter, null, false));
    setUpSource(source, true, controllerConfig);

    source.handleEvent(ResourceAction.UPDATED, cr, cr, null);

    verify(eventHandler, never()).handleEvent(any());
  }

  @Test
  void filtersOutEventsOnAddAndUpdate() {
    TestCustomResource cr = TestUtils.testCustomResource();

    OnAddFilter<TestCustomResource> onAddFilter = (res) -> false;
    OnUpdateFilter<TestCustomResource> onUpdatePredicate = (res, res2) -> false;
    source =
        new ControllerEventSource<>(new TestController(onAddFilter, onUpdatePredicate, null, true));
    setUpSource(source, true, controllerConfig);

    source.handleEvent(ResourceAction.ADDED, cr, null, null, null);
    source.handleEvent(ResourceAction.UPDATED, cr, cr, null, null);

    verify(eventHandler, never()).handleEvent(any());
  }

  @Test
  void genericFilterFiltersOutAddUpdateAndDeleteEvents() {
    TestCustomResource cr = TestUtils.testCustomResource();

    source = new ControllerEventSource<>(new TestController(null, null, res -> false, true));
    setUpSource(source, true, controllerConfig);

    source.handleEvent(ResourceAction.ADDED, cr, null, null, null);
    source.handleEvent(ResourceAction.UPDATED, cr, cr, null, null);
    source.handleEvent(ResourceAction.DELETED, cr, cr, true, null);

    verify(eventHandler, never()).handleEvent(any());
  }

  @Test
  void ownUpdateEchoIsFilteredOutByEventFilter() throws InterruptedException {
    // End-to-end smoke for the event-filter wiring on the controller path: an event for our
    // own write must not propagate. Detail-level filter scenarios are covered in
    // EventingDetailTest / EventFilterSupportTest.
    source = spy(new ControllerEventSource<>(new TestController(null, null, null, true)));
    setUpSource(source, true, controllerConfig);
    doReturn(Optional.empty()).when(source).get(any());

    var latch = sendForEventFilteringUpdate(2);
    source.onUpdate(testResourceWithVersion(1), testResourceWithVersion(2));
    latch.countDown();

    Thread.sleep(100);
    verify(eventHandler, never()).handleEvent(any());
  }

  @Test
  void foreignUpdateDuringFilteringPropagatesAsUpdate() {
    // An external event during the filter window must surface (not be filtered as own).
    source = spy(new ControllerEventSource<>(new TestController(null, null, null, true)));
    setUpSource(source, true, controllerConfig);

    var latch = sendForEventFilteringUpdate(2);
    source.onUpdate(testResourceWithVersion(2), testResourceWithVersion(3));
    latch.countDown();

    await().untilAsserted(() -> expectHandleEvent(3, 2));
  }

  @Test
  void deleteEventDuringFilteringPropagatesAsDelete() {
    // A DELETE arriving during the filter window must surface — the resource has gone,
    // so the filter must not silence it just because our own write is still tracking RVs.
    source = spy(new ControllerEventSource<>(new TestController(null, null, null, true)));
    setUpSource(source, true, controllerConfig);

    var latch = sendForEventFilteringUpdate(2);
    source.onDelete(testResourceWithVersion(3), false);
    latch.countDown();

    await()
        .untilAsserted(
            () -> {
              verify(eventHandler, atLeastOnce()).handleEvent(any());
              verify(source, atLeastOnce())
                  .handleEvent(eq(ResourceAction.DELETED), any(), any(), any(), any());
            });
  }

  @Test
  void multipleForeignEventsDuringFilteringMergeIntoSingleEvent() {
    // Several external events during one filter window collapse into a single
    // synthesized event spanning prev → latest seen.
    source = spy(new ControllerEventSource<>(new TestController(null, null, null, true)));
    setUpSource(source, true, controllerConfig);

    var latch = sendForEventFilteringUpdate(2);
    source.onUpdate(testResourceWithVersion(2), testResourceWithVersion(3));
    source.onUpdate(testResourceWithVersion(3), testResourceWithVersion(4));
    latch.countDown();

    await().untilAsserted(() -> expectHandleEvent(4, 2));
  }

  private void expectHandleEvent(int newResourceVersion, int oldResourceVersion) {
    verify(eventHandler, times(1)).handleEvent(any());
    verify(source, times(1))
        .handleEvent(
            eq(ResourceAction.UPDATED),
            argThat(r -> ("" + newResourceVersion).equals(r.getMetadata().getResourceVersion())),
            argThat(r -> ("" + oldResourceVersion).equals(r.getMetadata().getResourceVersion())),
            any(),
            any());
  }

  private TestCustomResource testResourceWithVersion(int v) {
    return withResourceVersion(TestUtils.testCustomResource1(), v);
  }

  private CountDownLatch sendForEventFilteringUpdate(int resourceVersion) {
    var testResource = TestUtils.testCustomResource1();
    return EventFilterTestUtils.sendForEventFilteringUpdate(
        source, testResource, r -> withResourceVersion(testResource, resourceVersion));
  }

  @SuppressWarnings("unchecked")
  private static class TestController extends Controller<TestCustomResource> {

    private static final Reconciler<TestCustomResource> reconciler =
        (resource, context) -> UpdateControl.noUpdate();

    private final EventSourceManager<TestCustomResource> eventSourceManager =
        mock(EventSourceManager.class);

    public TestController(
        OnAddFilter<TestCustomResource> onAddFilter,
        OnUpdateFilter<TestCustomResource> onUpdateFilter,
        GenericFilter<TestCustomResource> genericFilter,
        boolean defaultFilters) {
      super(
          reconciler,
          new TestConfiguration(true, onAddFilter, onUpdateFilter, genericFilter, defaultFilters),
          MockKubernetesClient.client(TestCustomResource.class));
    }

    public TestController(boolean generationAware) {
      super(
          reconciler,
          new TestConfiguration(generationAware, null, null, null, true),
          MockKubernetesClient.client(TestCustomResource.class));
    }

    @Override
    public EventSourceManager<TestCustomResource> getEventSourceManager() {
      return eventSourceManager;
    }

    @Override
    public boolean useFinalizer() {
      return true;
    }
  }

  private static class TestConfiguration
      extends ResolvedControllerConfiguration<TestCustomResource> {

    public TestConfiguration(
        boolean generationAware,
        OnAddFilter<TestCustomResource> onAddFilter,
        OnUpdateFilter<TestCustomResource> onUpdateFilter,
        GenericFilter<TestCustomResource> genericFilter,
        boolean defaultFilters) {
      super(
          "test",
          generationAware,
          null,
          null,
          null,
          null,
          FINALIZER,
          null,
          null,
          new BaseConfigurationService(),
          InformerConfiguration.builder(TestCustomResource.class)
              .withOnAddFilter(onAddFilter)
              .withOnUpdateFilter(onUpdateFilter)
              .withGenericFilter(genericFilter)
              .withComparableResourceVersions(true)
              .buildForController(),
          false,
          defaultFilters);
    }
  }
}
