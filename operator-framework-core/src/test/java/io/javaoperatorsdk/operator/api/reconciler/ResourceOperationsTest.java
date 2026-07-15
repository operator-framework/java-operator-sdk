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
package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.matcher.Matcher;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ResourceOperationsTest {

  private static final String FINALIZER_NAME = "test.javaoperatorsdk.io/finalizer";

  private Context<TestCustomResource> context;

  @SuppressWarnings("rawtypes")
  private Resource resourceOp;

  private ControllerEventSource<TestCustomResource> controllerEventSource;
  private ResourceOperations<TestCustomResource> resourceOperations;

  @SuppressWarnings("rawtypes")
  private ManagedInformerEventSource verbEventSource;

  @SuppressWarnings("rawtypes")
  private NamespaceableResource verbClientResource;

  @BeforeEach
  void setupMocks() {
    context = mock(Context.class);
    final var client = mock(KubernetesClient.class);
    final var mixedOperation = mock(MixedOperation.class);
    resourceOp = mock(Resource.class);
    controllerEventSource = mock(ControllerEventSource.class);
    final var controllerConfiguration = mock(ControllerConfiguration.class);

    var eventSourceRetriever = mock(EventSourceRetriever.class);

    when(context.getClient()).thenReturn(client);
    when(client.getKubernetesSerialization()).thenReturn(new KubernetesSerialization());
    when(context.eventSourceRetriever()).thenReturn(eventSourceRetriever);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
    when(controllerConfiguration.getFinalizerName()).thenReturn(FINALIZER_NAME);
    var configService = mock(ConfigurationService.class);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configService);
    when(configService.getResourceCloner())
        .thenReturn(
            new Cloner() {
              @Override
              public <R extends HasMetadata> R clone(R object) {
                return new KubernetesSerialization().clone(object);
              }
            });
    when(eventSourceRetriever.getControllerEventSource()).thenReturn(controllerEventSource);

    when(client.resources(TestCustomResource.class)).thenReturn(mixedOperation);
    when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
    when(mixedOperation.withName(any())).thenReturn(resourceOp);

    resourceOperations = new ResourceOperations<>(context);
  }

  @Test
  void addsFinalizer() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");

    when(context.getPrimaryResource()).thenReturn(resource);

    // Mock successful finalizer addition
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenAnswer(
            invocation -> {
              var res = TestUtils.testCustomResource1();
              res.getMetadata().setResourceVersion("2");
              res.addFinalizer(FINALIZER_NAME);
              return res;
            });

    var result = resourceOperations.addFinalizer(FINALIZER_NAME);

    assertThat(result).isNotNull();
    assertThat(result.hasFinalizer(FINALIZER_NAME)).isTrue();
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("2");
    verify(controllerEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void addsFinalizerWithSSA() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");

    when(context.getPrimaryResource()).thenReturn(resource);

    // Mock successful SSA finalizer addition
    when(controllerEventSource.updateAndCacheResource(any(), any(UnaryOperator.class)))
        .thenAnswer(
            invocation -> {
              var res = TestUtils.testCustomResource1();
              res.getMetadata().setResourceVersion("2");
              res.addFinalizer(FINALIZER_NAME);
              return res;
            });

    var result = resourceOperations.addFinalizerWithSSA(FINALIZER_NAME);

    assertThat(result).isNotNull();
    assertThat(result.hasFinalizer(FINALIZER_NAME)).isTrue();
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("2");
    verify(controllerEventSource, times(1)).updateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void removesFinalizer() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");
    resource.addFinalizer(FINALIZER_NAME);

    when(context.getPrimaryResource()).thenReturn(resource);

    // Mock successful finalizer removal
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenAnswer(
            invocation -> {
              var res = TestUtils.testCustomResource1();
              res.getMetadata().setResourceVersion("2");
              // finalizer is removed, so don't add it
              return res;
            });

    var result = resourceOperations.removeFinalizer(FINALIZER_NAME);

    assertThat(result).isNotNull();
    assertThat(result.hasFinalizer(FINALIZER_NAME)).isFalse();
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("2");
    verify(controllerEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void retriesAddingFinalizerWithoutSSA() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");

    when(context.getPrimaryResource()).thenReturn(resource);

    // First call throws conflict, second succeeds
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenThrow(new KubernetesClientException("Conflict", 409, null))
        .thenAnswer(
            invocation -> {
              var res = TestUtils.testCustomResource1();
              res.getMetadata().setResourceVersion("2");
              res.addFinalizer(FINALIZER_NAME);
              return res;
            });

    // Return fresh resource on retry
    when(resourceOp.get()).thenReturn(resource);

    var result = resourceOperations.addFinalizer(FINALIZER_NAME);

    assertThat(result).isNotNull();
    assertThat(result.hasFinalizer(FINALIZER_NAME)).isTrue();
    verify(controllerEventSource, times(2))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
    verify(resourceOp, times(1)).get();
  }

  @Test
  void nullResourceIsGracefullyHandledOnFinalizerRemovalRetry() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");
    resource.addFinalizer(FINALIZER_NAME);

    when(context.getPrimaryResource()).thenReturn(resource);

    // First call throws conflict
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenThrow(new KubernetesClientException("Conflict", 409, null));

    // Return null on retry (resource was deleted)
    when(resourceOp.get()).thenReturn(null);

    resourceOperations.removeFinalizer(FINALIZER_NAME);

    verify(controllerEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
    verify(resourceOp, times(1)).get();
  }

  @Test
  void retriesFinalizerRemovalWithFreshResource() {
    var originalResource = TestUtils.testCustomResource1();
    originalResource.getMetadata().setResourceVersion("1");
    originalResource.addFinalizer(FINALIZER_NAME);

    when(context.getPrimaryResource()).thenReturn(originalResource);

    // First call throws unprocessable (422), second succeeds
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenThrow(new KubernetesClientException("Unprocessable", 422, null))
        .thenAnswer(
            invocation -> {
              var res = TestUtils.testCustomResource1();
              res.getMetadata().setResourceVersion("3");
              // finalizer should be removed
              return res;
            });

    // Return fresh resource with newer version on retry
    var freshResource = TestUtils.testCustomResource1();
    freshResource.getMetadata().setResourceVersion("2");
    freshResource.addFinalizer(FINALIZER_NAME);
    when(resourceOp.get()).thenReturn(freshResource);

    var result = resourceOperations.removeFinalizer(FINALIZER_NAME);

    assertThat(result).isNotNull();
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("3");
    assertThat(result.hasFinalizer(FINALIZER_NAME)).isFalse();
    verify(controllerEventSource, times(2))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
    verify(resourceOp, times(1)).get();
  }

  @Test
  void resourcePatchWithSingleEventSource() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");

    var updatedResource = TestUtils.testCustomResource1();
    updatedResource.getMetadata().setResourceVersion("2");

    var eventSourceRetriever = mock(EventSourceRetriever.class);
    var managedEventSource = mock(ManagedInformerEventSource.class);

    when(context.eventSourceRetriever()).thenReturn(eventSourceRetriever);
    when(eventSourceRetriever.getEventSourcesFor(TestCustomResource.class))
        .thenReturn(List.of(managedEventSource));
    when(managedEventSource.eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class)))
        .thenReturn(updatedResource);

    var result = resourceOperations.resourcePatch(resource, UnaryOperator.identity());

    assertThat(result).isNotNull();
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("2");
    verify(managedEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void resourcePatchThrowsWhenNoEventSourceFound() {
    var resource = TestUtils.testCustomResource1();
    var eventSourceRetriever = mock(EventSourceRetriever.class);

    when(context.eventSourceRetriever()).thenReturn(eventSourceRetriever);
    when(eventSourceRetriever.getEventSourcesFor(TestCustomResource.class))
        .thenReturn(Collections.emptyList());

    var exception =
        assertThrows(
            IllegalStateException.class,
            () -> resourceOperations.resourcePatch(resource, UnaryOperator.identity()));

    assertThat(exception.getMessage()).contains("No event source found for type");
  }

  @Test
  void resourcePatchUsesFirstEventSourceIfMultipleEventSourcesPresent() {
    var resource = TestUtils.testCustomResource1();
    var eventSourceRetriever = mock(EventSourceRetriever.class);
    var eventSource1 = mock(ManagedInformerEventSource.class);
    var eventSource2 = mock(ManagedInformerEventSource.class);

    when(context.eventSourceRetriever()).thenReturn(eventSourceRetriever);
    when(eventSourceRetriever.getEventSourcesFor(TestCustomResource.class))
        .thenReturn(List.of(eventSource1, eventSource2));

    resourceOperations.resourcePatch(resource, UnaryOperator.identity());

    verify(eventSource1, times(1))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void resourcePatchThrowsWhenEventSourceIsNotManagedInformer() {
    var resource = TestUtils.testCustomResource1();
    var eventSourceRetriever = mock(EventSourceRetriever.class);
    var nonManagedEventSource = mock(EventSource.class);

    when(context.eventSourceRetriever()).thenReturn(eventSourceRetriever);
    when(eventSourceRetriever.getEventSourcesFor(TestCustomResource.class))
        .thenReturn(List.of(nonManagedEventSource));

    var exception =
        assertThrows(
            IllegalStateException.class,
            () -> resourceOperations.resourcePatch(resource, UnaryOperator.identity()));

    assertThat(exception.getMessage()).contains("Target event source must be a subclass off");
    assertThat(exception.getMessage()).contains("ManagedInformerEventSource");
  }

  @Test
  void matcherMatchingSkipsUpdateAndReturnsActual() {
    var desired = TestUtils.testCustomResource1();
    var actual = TestUtils.testCustomResource1();
    var ies = mock(ManagedInformerEventSource.class);
    var matcher = mock(Matcher.class);
    when(matcher.matches(desired, actual, context)).thenReturn(true);

    var result =
        resourceOperations.resourcePatch(
            desired,
            actual,
            UnaryOperator.identity(),
            ies,
            ResourceOperations.Options.matchAndFilter(matcher));

    assertThat(result).isSameAs(actual);
    verify(ies, never()).eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
    verify(ies, never()).updateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void matcherNotMatchingProceedsToEventFilteringUpdate() {
    var desired = TestUtils.testCustomResource1();
    var actual = TestUtils.testCustomResource1();
    var updated = TestUtils.testCustomResource1();
    var ies = mock(ManagedInformerEventSource.class);
    var matcher = mock(Matcher.class);
    when(matcher.matches(desired, actual, context)).thenReturn(false);
    when(ies.eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class)))
        .thenReturn(updated);

    var result =
        resourceOperations.resourcePatch(
            desired,
            actual,
            UnaryOperator.identity(),
            ies,
            ResourceOperations.Options.matchAndFilter(matcher));

    assertThat(result).isSameAs(updated);
    verify(ies, times(1))
        .eventFilteringUpdateAndCacheResource(eq(desired), any(UnaryOperator.class));
  }

  @Test
  void onlyCacheModeSkipsEventFiltering() {
    var desired = TestUtils.testCustomResource1();
    var ies = mock(ManagedInformerEventSource.class);
    when(ies.updateAndCacheResource(any(), any(UnaryOperator.class))).thenReturn(desired);

    resourceOperations.resourcePatch(
        desired, null, UnaryOperator.identity(), ies, ResourceOperations.Options.cacheOnly());

    verify(ies, times(1)).updateAndCacheResource(eq(desired), any(UnaryOperator.class));
    verify(ies, never()).eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void filterWithOptimisticLockingFiltersWhenResourceVersionPresent() {
    var desired = TestUtils.testCustomResource1();
    desired.getMetadata().setResourceVersion("1");
    var ies = mock(ManagedInformerEventSource.class);
    when(ies.eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class)))
        .thenReturn(desired);

    resourceOperations.resourcePatch(
        desired,
        null,
        UnaryOperator.identity(),
        ies,
        ResourceOperations.Options.filterWithOptimisticLocking());

    verify(ies, times(1))
        .eventFilteringUpdateAndCacheResource(eq(desired), any(UnaryOperator.class));
    verify(ies, never()).updateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void createRejectsMatcher() {
    var resource = TestUtils.testCustomResource1();
    var matcher = mock(Matcher.class);

    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                resourceOperations.create(resource, ResourceOperations.Options.cacheOnly(matcher)));

    assertThat(exception.getMessage()).contains("does not support matcher");
  }

  // ---------------------------------------------------------------------------
  // High-level update / create / patch verbs with different Options variations.
  // These go through the full public API (resolving the event source from the
  // retriever and invoking the real client verb), verifying both the routing
  // (filter vs cache) and which underlying Kubernetes operation is used.
  // ---------------------------------------------------------------------------

  /**
   * Wires a {@link ManagedInformerEventSource} into the retriever and a client {@link Resource} so
   * that both cache paths actually run the update operation, letting us assert which client verb is
   * invoked. Returns the resource the client verbs are stubbed to return.
   */
  @SuppressWarnings("rawtypes")
  private TestCustomResource wireVerbMocks() {
    verbEventSource = mock(ManagedInformerEventSource.class);
    when(context.eventSourceRetriever().getEventSourcesFor(TestCustomResource.class))
        .thenReturn(List.of(verbEventSource));

    verbClientResource = mock(NamespaceableResource.class);
    when(context.getClient().resource(any(HasMetadata.class))).thenReturn(verbClientResource);

    var updated = TestUtils.testCustomResource1();
    updated.getMetadata().setResourceVersion("999");
    when(verbClientResource.update()).thenReturn(updated);
    when(verbClientResource.create()).thenReturn(updated);
    when(verbClientResource.updateStatus()).thenReturn(updated);
    when(verbClientResource.patch()).thenReturn(updated);
    when(verbClientResource.patch(any(PatchContext.class))).thenReturn(updated);

    // both cache paths execute the update operation so the underlying client verb runs
    Answer<Object> runOperation =
        invocation -> ((UnaryOperator) invocation.getArgument(1)).apply(invocation.getArgument(0));
    when(verbEventSource.eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class)))
        .thenAnswer(runOperation);
    when(verbEventSource.updateAndCacheResource(any(), any(UnaryOperator.class)))
        .thenAnswer(runOperation);
    return updated;
  }

  // ---- update -------------------------------------------------------------

  @Test
  void updateCacheOnlyCachesAndCallsClientUpdate() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");
    var updated = wireVerbMocks();

    var result = resourceOperations.update(resource, ResourceOperations.Options.cacheOnly());

    assertThat(result).isSameAs(updated);
    verify(verbEventSource, times(1))
        .updateAndCacheResource(eq(resource), any(UnaryOperator.class));
    verify(verbEventSource, never())
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
    verify(verbClientResource, times(1)).update();
  }

  @Test
  void updateForceFilterFiltersAndCallsClientUpdate() {
    var resource = TestUtils.testCustomResource1();
    var updated = wireVerbMocks();

    var result =
        resourceOperations.update(resource, ResourceOperations.Options.forceFilterEvents());

    assertThat(result).isSameAs(updated);
    verify(verbEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(eq(resource), any(UnaryOperator.class));
    verify(verbEventSource, never()).updateAndCacheResource(any(), any(UnaryOperator.class));
    verify(verbClientResource, times(1)).update();
  }

  @Test
  void updateFilterWithOptimisticLockingFiltersWhenResourceVersionPresent() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");
    wireVerbMocks();

    resourceOperations.update(resource, ResourceOperations.Options.filterWithOptimisticLocking());

    verify(verbEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(eq(resource), any(UnaryOperator.class));
    verify(verbEventSource, never()).updateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void updateWithMatcherMatchingSkipsWrite() {
    var desired = TestUtils.testCustomResource1();
    var actual = TestUtils.testCustomResource1();
    wireVerbMocks();
    when(verbEventSource.get(any())).thenReturn(Optional.of(actual));
    var matcher = mock(Matcher.class);
    when(matcher.matches(any(), any(), any())).thenReturn(true);

    var result =
        resourceOperations.update(desired, ResourceOperations.Options.matchAndFilter(matcher));

    assertThat(result).isSameAs(actual);
    verify(verbEventSource, never())
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
    verify(verbEventSource, never()).updateAndCacheResource(any(), any(UnaryOperator.class));
    verify(verbClientResource, never()).update();
  }

  @Test
  void updateWithMatcherNotMatchingFiltersAndWrites() {
    var desired = TestUtils.testCustomResource1();
    var actual = TestUtils.testCustomResource1();
    var updated = wireVerbMocks();
    when(verbEventSource.get(any())).thenReturn(Optional.of(actual));
    var matcher = mock(Matcher.class);
    when(matcher.matches(any(), any(), any())).thenReturn(false);

    var result =
        resourceOperations.update(desired, ResourceOperations.Options.matchAndFilter(matcher));

    assertThat(result).isSameAs(updated);
    verify(verbEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(eq(desired), any(UnaryOperator.class));
    verify(verbClientResource, times(1)).update();
  }

  // ---- create -------------------------------------------------------------

  @Test
  void createDefaultForceFiltersAndCallsClientCreate() {
    var resource = TestUtils.testCustomResource1();
    var updated = wireVerbMocks();

    var result = resourceOperations.create(resource);

    assertThat(result).isSameAs(updated);
    verify(verbEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(eq(resource), any(UnaryOperator.class));
    verify(verbEventSource, never()).updateAndCacheResource(any(), any(UnaryOperator.class));
    verify(verbClientResource, times(1)).create();
  }

  @Test
  void createCacheOnlyCachesAndCallsClientCreate() {
    var resource = TestUtils.testCustomResource1();
    var updated = wireVerbMocks();

    var result = resourceOperations.create(resource, ResourceOperations.Options.cacheOnly());

    assertThat(result).isSameAs(updated);
    verify(verbEventSource, times(1))
        .updateAndCacheResource(eq(resource), any(UnaryOperator.class));
    verify(verbEventSource, never())
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
    verify(verbClientResource, times(1)).create();
  }

  @Test
  void createWithNullEventSourceFallsBackToForceFilter() {
    var resource = TestUtils.testCustomResource1();
    wireVerbMocks();

    // a null informer event source falls back to the default create(), which force-filters
    resourceOperations.create(resource, null, ResourceOperations.Options.cacheOnly());

    verify(verbEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(eq(resource), any(UnaryOperator.class));
    verify(verbEventSource, never()).updateAndCacheResource(any(), any(UnaryOperator.class));
  }

  // ---- patch / status verbs ----------------------------------------------

  @Test
  void jsonMergePatchCacheOnlyCallsClientPatch() {
    var resource = TestUtils.testCustomResource1();
    var updated = wireVerbMocks();

    var result =
        resourceOperations.jsonMergePatch(resource, ResourceOperations.Options.cacheOnly());

    assertThat(result).isSameAs(updated);
    verify(verbEventSource, times(1))
        .updateAndCacheResource(eq(resource), any(UnaryOperator.class));
    verify(verbClientResource, times(1)).patch();
  }

  @Test
  void serverSideApplyCacheOnlyCallsClientSsaPatch() {
    var resource = TestUtils.testCustomResource1();
    var updated = wireVerbMocks();

    var result =
        resourceOperations.serverSideApply(resource, ResourceOperations.Options.cacheOnly());

    assertThat(result).isSameAs(updated);
    verify(verbEventSource, times(1))
        .updateAndCacheResource(eq(resource), any(UnaryOperator.class));
    verify(verbClientResource, times(1)).patch(any(PatchContext.class));
  }

  @Test
  void updateStatusCacheOnlyCallsClientUpdateStatus() {
    var resource = TestUtils.testCustomResource1();
    var updated = wireVerbMocks();

    var result = resourceOperations.updateStatus(resource, ResourceOperations.Options.cacheOnly());

    assertThat(result).isSameAs(updated);
    verify(verbEventSource, times(1))
        .updateAndCacheResource(eq(resource), any(UnaryOperator.class));
    verify(verbClientResource, times(1)).updateStatus();
  }
}
