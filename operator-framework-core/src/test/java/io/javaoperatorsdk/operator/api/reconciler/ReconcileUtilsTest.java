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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReconcileUtilsTest {

  private static final String FINALIZER_NAME = "test.javaoperatorsdk.io/finalizer";

  private Context<TestCustomResource> context;
  private KubernetesClient client;
  private MixedOperation mixedOperation;
  private Resource resourceOp;
  private ControllerEventSource<TestCustomResource> controllerEventSource;
  private ControllerConfiguration<TestCustomResource> controllerConfiguration;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setupMocks() {
    context = mock(Context.class);
    client = mock(KubernetesClient.class);
    mixedOperation = mock(MixedOperation.class);
    resourceOp = mock(Resource.class);
    controllerEventSource = mock(ControllerEventSource.class);
    controllerConfiguration = mock(ControllerConfiguration.class);

    var eventSourceRetriever = mock(EventSourceRetriever.class);

    when(context.getClient()).thenReturn(client);
    when(context.eventSourceRetriever()).thenReturn(eventSourceRetriever);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
    when(controllerConfiguration.getFinalizerName()).thenReturn(FINALIZER_NAME);
    when(eventSourceRetriever.getControllerEventSource()).thenReturn(controllerEventSource);

    when(client.resources(TestCustomResource.class)).thenReturn(mixedOperation);
    when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
    when(mixedOperation.withName(any())).thenReturn(resourceOp);
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

    var result = ReconcileUtils.addFinalizer(context, FINALIZER_NAME);

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
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenAnswer(
            invocation -> {
              var res = TestUtils.testCustomResource1();
              res.getMetadata().setResourceVersion("2");
              res.addFinalizer(FINALIZER_NAME);
              return res;
            });

    var result = ReconcileUtils.addFinalizerWithSSA(context, FINALIZER_NAME);

    assertThat(result).isNotNull();
    assertThat(result.hasFinalizer(FINALIZER_NAME)).isTrue();
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("2");
    verify(controllerEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
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

    var result = ReconcileUtils.removeFinalizer(context, FINALIZER_NAME);

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

    var result = ReconcileUtils.addFinalizer(context, FINALIZER_NAME);

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

    ReconcileUtils.removeFinalizer(context, FINALIZER_NAME);

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

    var result = ReconcileUtils.removeFinalizer(context, FINALIZER_NAME);

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

    var result = ReconcileUtils.resourcePatch(context, resource, UnaryOperator.identity());

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
            () -> ReconcileUtils.resourcePatch(context, resource, UnaryOperator.identity()));

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

    ReconcileUtils.resourcePatch(context, resource, UnaryOperator.identity());

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
            () -> ReconcileUtils.resourcePatch(context, resource, UnaryOperator.identity()));

    assertThat(exception.getMessage()).contains("Target event source must be a subclass off");
    assertThat(exception.getMessage()).contains("ManagedInformerEventSource");
  }

  @Test
  void latestDistinctKeepsOnlyLatestResourceVersion() {
    // Create multiple resources with same name and namespace but different versions
    HasMetadata pod1v1 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod1")
                    .withNamespace("default")
                    .withResourceVersion("100")
                    .build())
            .build();

    HasMetadata pod1v2 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod1")
                    .withNamespace("default")
                    .withResourceVersion("200")
                    .build())
            .build();

    HasMetadata pod1v3 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod1")
                    .withNamespace("default")
                    .withResourceVersion("150")
                    .build())
            .build();

    // Create a resource with different name
    HasMetadata pod2v1 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod2")
                    .withNamespace("default")
                    .withResourceVersion("100")
                    .build())
            .build();

    // Create a resource with same name but different namespace
    HasMetadata pod1OtherNsv1 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod1")
                    .withNamespace("other")
                    .withResourceVersion("50")
                    .build())
            .build();

    Collection<HasMetadata> result =
        Stream.of(pod1v1, pod1v2, pod1v3, pod2v1, pod1OtherNsv1)
            .collect(ReconcileUtils.latestDistinct());

    // Should have 3 resources: pod1 in default (latest version 200), pod2 in default, and pod1 in
    // other
    assertThat(result).hasSize(3);

    // Find pod1 in default namespace - should have version 200
    HasMetadata pod1InDefault =
        result.stream()
            .filter(
                r ->
                    "pod1".equals(r.getMetadata().getName())
                        && "default".equals(r.getMetadata().getNamespace()))
            .findFirst()
            .orElseThrow();
    assertThat(pod1InDefault.getMetadata().getResourceVersion()).isEqualTo("200");

    // Find pod2 in default namespace - should exist
    HasMetadata pod2InDefault =
        result.stream()
            .filter(
                r ->
                    "pod2".equals(r.getMetadata().getName())
                        && "default".equals(r.getMetadata().getNamespace()))
            .findFirst()
            .orElseThrow();
    assertThat(pod2InDefault.getMetadata().getResourceVersion()).isEqualTo("100");

    // Find pod1 in other namespace - should exist
    HasMetadata pod1InOther =
        result.stream()
            .filter(
                r ->
                    "pod1".equals(r.getMetadata().getName())
                        && "other".equals(r.getMetadata().getNamespace()))
            .findFirst()
            .orElseThrow();
    assertThat(pod1InOther.getMetadata().getResourceVersion()).isEqualTo("50");
  }

  @Test
  void latestDistinctHandlesEmptyStream() {
    Collection<HasMetadata> result =
        Stream.<HasMetadata>empty().collect(ReconcileUtils.latestDistinct());

    assertThat(result).isEmpty();
  }

  @Test
  void latestDistinctHandlesSingleResource() {
    HasMetadata pod =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod1")
                    .withNamespace("default")
                    .withResourceVersion("100")
                    .build())
            .build();

    Collection<HasMetadata> result = Stream.of(pod).collect(ReconcileUtils.latestDistinct());

    assertThat(result).hasSize(1);
    assertThat(result).contains(pod);
  }

  @Test
  void latestDistinctComparesNumericVersionsCorrectly() {
    // Test that version 1000 is greater than version 999 (not lexicographic)
    HasMetadata podV999 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod1")
                    .withNamespace("default")
                    .withResourceVersion("999")
                    .build())
            .build();

    HasMetadata podV1000 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod1")
                    .withNamespace("default")
                    .withResourceVersion("1000")
                    .build())
            .build();

    Collection<HasMetadata> result =
        Stream.of(podV999, podV1000).collect(ReconcileUtils.latestDistinct());

    assertThat(result).hasSize(1);
    HasMetadata resultPod = result.iterator().next();
    assertThat(resultPod.getMetadata().getResourceVersion()).isEqualTo("1000");
  }

  @Test
  void latestDistinctListReturnsListType() {
    Pod pod1v1 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod1")
                    .withNamespace("default")
                    .withResourceVersion("100")
                    .build())
            .build();

    Pod pod1v2 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod1")
                    .withNamespace("default")
                    .withResourceVersion("200")
                    .build())
            .build();

    Pod pod2v1 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod2")
                    .withNamespace("default")
                    .withResourceVersion("100")
                    .build())
            .build();

    List<Pod> result =
        Stream.of(pod1v1, pod1v2, pod2v1).collect(ReconcileUtils.latestDistinctList());

    assertThat(result).isInstanceOf(List.class);
    assertThat(result).hasSize(2);

    // Verify the list contains the correct resources
    Pod pod1 =
        result.stream()
            .filter(r -> "pod1".equals(r.getMetadata().getName()))
            .findFirst()
            .orElseThrow();
    assertThat(pod1.getMetadata().getResourceVersion()).isEqualTo("200");
  }

  @Test
  void latestDistinctSetReturnsSetType() {
    Pod pod1v1 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod1")
                    .withNamespace("default")
                    .withResourceVersion("100")
                    .build())
            .build();

    Pod pod1v2 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod1")
                    .withNamespace("default")
                    .withResourceVersion("200")
                    .build())
            .build();

    Pod pod2v1 =
        new PodBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("pod2")
                    .withNamespace("default")
                    .withResourceVersion("100")
                    .build())
            .build();

    Set<Pod> result = Stream.of(pod1v1, pod1v2, pod2v1).collect(ReconcileUtils.latestDistinctSet());

    assertThat(result).isInstanceOf(java.util.Set.class);
    assertThat(result).hasSize(2);

    // Verify the set contains the correct resources
    Pod pod1 =
        result.stream()
            .filter(r -> "pod1".equals(r.getMetadata().getName()))
            .findFirst()
            .orElseThrow();
    assertThat(pod1.getMetadata().getResourceVersion()).isEqualTo("200");
  }

  @Test
  void latestDistinctListHandlesEmptyStream() {
    List<HasMetadata> result =
        Stream.<HasMetadata>empty().collect(ReconcileUtils.latestDistinctList());

    assertThat(result).isEmpty();
    assertThat(result).isInstanceOf(List.class);
  }

  @Test
  void latestDistinctSetHandlesEmptyStream() {
    Set<HasMetadata> result =
        Stream.<HasMetadata>empty().collect(ReconcileUtils.latestDistinctSet());

    assertThat(result).isEmpty();
    assertThat(result).isInstanceOf(Set.class);
  }

}
