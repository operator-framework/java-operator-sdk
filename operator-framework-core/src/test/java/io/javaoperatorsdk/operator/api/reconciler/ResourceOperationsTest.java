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
import java.util.function.UnaryOperator;

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
import static org.mockito.Mockito.*;

class ResourceOperationsTest {

  private static final String FINALIZER_NAME = "test.javaoperatorsdk.io/finalizer";

  private Context<TestCustomResource> context;
  private KubernetesClient client;
  private MixedOperation mixedOperation;
  private Resource resourceOp;
  private ControllerEventSource<TestCustomResource> controllerEventSource;
  private ControllerConfiguration<TestCustomResource> controllerConfiguration;
  private ResourceOperations<TestCustomResource> resourceOperations;

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
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
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

    var result = resourceOperations.resourcePatch(context, resource, UnaryOperator.identity());

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
            () -> resourceOperations.resourcePatch(context, resource, UnaryOperator.identity()));

    assertThat(exception.getMessage()).contains("No event source found for type");
  }

  @Test
  void resourcePatchThrowsWhenMultipleEventSourcesFound() {
    var resource = TestUtils.testCustomResource1();
    var eventSourceRetriever = mock(EventSourceRetriever.class);
    var eventSource1 = mock(ManagedInformerEventSource.class);
    var eventSource2 = mock(ManagedInformerEventSource.class);

    when(context.eventSourceRetriever()).thenReturn(eventSourceRetriever);
    when(eventSourceRetriever.getEventSourcesFor(TestCustomResource.class))
        .thenReturn(List.of(eventSource1, eventSource2));

    var exception =
        assertThrows(
            IllegalStateException.class,
            () -> resourceOperations.resourcePatch(context, resource, UnaryOperator.identity()));

    assertThat(exception.getMessage()).contains("Multiple event sources found for");
    assertThat(exception.getMessage()).contains("please provide the target event source");
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
            () -> resourceOperations.resourcePatch(context, resource, UnaryOperator.identity()));

    assertThat(exception.getMessage()).contains("Target event source must be a subclass off");
    assertThat(exception.getMessage()).contains("ManagedInformerEventSource");
  }
}
