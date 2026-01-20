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
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClientException;
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
import static org.assertj.core.api.Assertions.assertThat;
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

    setUpSource(new ControllerEventSource<>(testController), true, controllerConfig);
  }

  @Test
  void skipsEventHandlingIfGenerationNotIncreased() {
    TestCustomResource customResource = TestUtils.testCustomResource();
    customResource.getMetadata().setFinalizers(List.of(FINALIZER));
    customResource.getMetadata().setGeneration(2L);

    TestCustomResource oldCustomResource = TestUtils.testCustomResource();
    oldCustomResource.getMetadata().setFinalizers(List.of(FINALIZER));

    source.handleEvent(ResourceAction.UPDATED, customResource, oldCustomResource, null, false);
    verify(eventHandler, times(1)).handleEvent(any());

    source.handleEvent(ResourceAction.UPDATED, customResource, customResource, null, false);
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  void dontSkipEventHandlingIfMarkedForDeletion() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, false);
    verify(eventHandler, times(1)).handleEvent(any());

    // mark for deletion
    customResource1.getMetadata().setDeletionTimestamp(LocalDateTime.now().toString());
    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, false);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void normalExecutionIfGenerationChanges() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, false);
    verify(eventHandler, times(1)).handleEvent(any());

    customResource1.getMetadata().setGeneration(2L);
    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, false);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void handlesAllEventIfNotGenerationAware() {
    source = new ControllerEventSource<>(new TestController(false));
    setup();

    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, false);
    verify(eventHandler, times(1)).handleEvent(any());

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, false);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void eventWithNoGenerationProcessedIfNoFinalizer() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, false);

    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  void callsBroadcastsOnResourceEvents() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.handleEvent(ResourceAction.UPDATED, customResource1, customResource1, null, false);

    verify(testController.getEventSourceManager(), times(1))
        .broadcastOnResourceEvent(
            eq(ResourceAction.UPDATED), eq(customResource1), eq(customResource1));
  }

  @Test
  void filtersOutEventsOnAddAndUpdate() {
    TestCustomResource cr = TestUtils.testCustomResource();

    OnAddFilter<TestCustomResource> onAddFilter = (res) -> false;
    OnUpdateFilter<TestCustomResource> onUpdatePredicate = (res, res2) -> false;
    source = new ControllerEventSource<>(new TestController(onAddFilter, onUpdatePredicate, null));
    setUpSource(source, true, controllerConfig);

    source.handleEvent(ResourceAction.ADDED, cr, null, null, false);
    source.handleEvent(ResourceAction.UPDATED, cr, cr, null, false);

    verify(eventHandler, never()).handleEvent(any());
  }

  @Test
  void genericFilterFiltersOutAddUpdateAndDeleteEvents() {
    TestCustomResource cr = TestUtils.testCustomResource();

    source = new ControllerEventSource<>(new TestController(null, null, res -> false));
    setUpSource(source, true, controllerConfig);

    source.handleEvent(ResourceAction.ADDED, cr, null, null, false);
    source.handleEvent(ResourceAction.UPDATED, cr, cr, null, false);
    source.handleEvent(ResourceAction.DELETED, cr, cr, true, false);

    verify(eventHandler, never()).handleEvent(any());
  }

  @Test
  void testEventFilteringBasicScenario() throws InterruptedException {
    source = spy(new ControllerEventSource<>(new TestController(null, null, null)));
    setUpSource(source, true, controllerConfig);

    var latch = sendForEventFilteringUpdate(2);
    source.onUpdate(testResourceWithVersion(1), testResourceWithVersion(2));
    latch.countDown();

    Thread.sleep(100);
    verify(eventHandler, never()).handleEvent(any());
  }

  @Test
  void eventFilteringNewEventDuringUpdate() {
    source = spy(new ControllerEventSource<>(new TestController(null, null, null)));
    setUpSource(source, true, controllerConfig);

    var latch = sendForEventFilteringUpdate(2);
    source.onUpdate(testResourceWithVersion(2), testResourceWithVersion(3));
    latch.countDown();

    await().untilAsserted(() -> expectHandleEvent(3, 2));
  }

  @Disabled("todo")
  @Test
  void eventFilteringMoreNewEventsDuringUpdate() {
    source = spy(new ControllerEventSource<>(new TestController(null, null, null)));
    setUpSource(source, true, controllerConfig);

    var latch = sendForEventFilteringUpdate(2);
    source.onUpdate(testResourceWithVersion(2), testResourceWithVersion(3));
    source.onUpdate(testResourceWithVersion(3), testResourceWithVersion(4));
    latch.countDown();

    await().untilAsserted(() -> expectHandleEvent(4, 2));
  }

  @Test
  void eventFilteringExceptionDuringUpdate() {
    source = spy(new ControllerEventSource<>(new TestController(null, null, null)));
    setUpSource(source, true, controllerConfig);

    var latch =
        EventFilterTestUtils.sendForEventFilteringUpdate(
            source,
            TestUtils.testCustomResource1(),
            r -> {
              throw new KubernetesClientException("fake");
            });
    source.onUpdate(testResourceWithVersion(1), testResourceWithVersion(2));
    latch.countDown();

    expectHandleEvent(2, 1);
  }

  private void expectHandleEvent(int newResourceVersion, int oldResourceVersion) {
    await()
        .untilAsserted(
            () -> {
              verify(eventHandler, times(1)).handleEvent(any());
              verify(source, times(1))
                  .handleEvent(
                      eq(ResourceAction.UPDATED),
                      argThat(
                          r -> {
                            assertThat(r.getMetadata().getResourceVersion())
                                .isEqualTo("" + newResourceVersion);
                            return true;
                          }),
                      argThat(
                          r -> {
                            assertThat(r.getMetadata().getResourceVersion())
                                .isEqualTo("" + oldResourceVersion);
                            return true;
                          }),
                      isNull(),
                      eq(false));
            });
  }

  private TestCustomResource testResourceWithVersion(int v) {
    return withResourceVersion(TestUtils.testCustomResource1(), v);
  }

  private CountDownLatch sendForEventFilteringUpdate(int v) {
    return sendForEventFilteringUpdate(TestUtils.testCustomResource1(), v);
  }

  private CountDownLatch sendForEventFilteringUpdate(
      TestCustomResource testResource, int resourceVersion) {
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
        GenericFilter<TestCustomResource> genericFilter) {
      super(
          reconciler,
          new TestConfiguration(true, onAddFilter, onUpdateFilter, genericFilter),
          MockKubernetesClient.client(TestCustomResource.class));
    }

    public TestController(boolean generationAware) {
      super(
          reconciler,
          new TestConfiguration(generationAware, null, null, null),
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
        GenericFilter<TestCustomResource> genericFilter) {
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
          false);
    }
  }
}
