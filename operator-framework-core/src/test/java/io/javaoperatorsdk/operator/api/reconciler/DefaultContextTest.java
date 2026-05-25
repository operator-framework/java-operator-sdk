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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.NoEventSourceForClassException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultContextTest {

  private DefaultContext<?> context;
  private Controller<HasMetadata> mockController;
  private EventSourceManager<HasMetadata> mockManager;

  @BeforeEach
  void setUp() {
    mockController = mock();
    mockManager = mock();
    when(mockController.getEventSourceManager()).thenReturn(mockManager);

    context = new DefaultContext<>(null, mockController, new Secret(), false, false);
  }

  @Test
  void getSecondaryResourceReturnsEmptyOptionalOnNonActivatedDRType() {
    when(mockController.workflowContainsDependentForType(ConfigMap.class)).thenReturn(true);
    when(mockManager.getEventSourceFor(any(), any()))
        .thenThrow(new NoEventSourceForClassException(ConfigMap.class));

    var res = context.getSecondaryResource(ConfigMap.class);
    assertThat(res).isEmpty();
  }

  @Test
  void getSecondaryResourceByNameAndNamespaceReturnsFromCacheFastPath() {
    final var cm =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName("cm-foo")
            .withNamespace("ns")
            .endMetadata()
            .build();

    final ManagedInformerEventSource<ConfigMap, HasMetadata, ?> cachingEventSource = mock();
    when(cachingEventSource.get(new ResourceID("cm-foo", "ns"))).thenReturn(Optional.of(cm));
    when(mockManager.getEventSourceFor(ConfigMap.class, "es-name")).thenReturn(cachingEventSource);

    final var res = context.getSecondaryResource(ConfigMap.class, "es-name", "cm-foo", "ns");

    assertThat(res).contains(cm);
    verify(cachingEventSource).get(new ResourceID("cm-foo", "ns"));
  }

  @Test
  void getSecondaryResourceByNameAndNamespaceReturnsEmptyOnCacheMiss() {
    final ManagedInformerEventSource<ConfigMap, HasMetadata, ?> cachingEventSource = mock();
    when(cachingEventSource.get(new ResourceID("missing", "ns"))).thenReturn(Optional.empty());
    when(mockManager.getEventSourceFor(ConfigMap.class, "es-name")).thenReturn(cachingEventSource);

    assertThat(context.getSecondaryResource(ConfigMap.class, "es-name", "missing", "ns")).isEmpty();
  }

  @Test
  void getSecondaryResourceByNameAndNamespaceFallsBackToGetSecondaryResources() {
    final var match =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName("cm-foo")
            .withNamespace("ns")
            .endMetadata()
            .build();
    final var other =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName("cm-bar")
            .withNamespace("ns")
            .endMetadata()
            .build();

    final EventSource<ConfigMap, HasMetadata> nonCachingEventSource = mock();
    when(nonCachingEventSource.getSecondaryResources(any())).thenReturn(Set.of(match, other));
    when(mockManager.getEventSourceFor(ConfigMap.class, "es-name"))
        .thenReturn(nonCachingEventSource);

    final var res = context.getSecondaryResource(ConfigMap.class, "es-name", "cm-foo", "ns");

    assertThat(res).contains(match);
  }

  @Test
  void getSecondaryResourceByNameAndNamespaceFallbackReturnsEmptyWhenNoMatch() {
    final var other =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName("cm-other")
            .withNamespace("ns")
            .endMetadata()
            .build();

    final EventSource<ConfigMap, HasMetadata> nonCachingEventSource = mock();
    when(nonCachingEventSource.getSecondaryResources(any())).thenReturn(Set.of(other));
    when(mockManager.getEventSourceFor(ConfigMap.class, "es-name"))
        .thenReturn(nonCachingEventSource);

    assertThat(context.getSecondaryResource(ConfigMap.class, "es-name", "missing", "ns")).isEmpty();
  }

  @Test
  void getSecondaryResourceByNameAndNamespaceRethrowsWhenNoEventSourceAndNotWorkflowManaged() {
    when(mockManager.getEventSourceFor(ConfigMap.class, "es-name"))
        .thenThrow(new NoEventSourceForClassException(ConfigMap.class));

    assertThatThrownBy(
            () -> context.getSecondaryResource(ConfigMap.class, "es-name", "cm-foo", "ns"))
        .isInstanceOf(NoEventSourceForClassException.class);
  }

  @Test
  void getSecondaryResourceByNameAndNamespaceReturnsEmptyWhenNoEventSourceButWorkflowManaged() {
    when(mockManager.getEventSourceFor(ConfigMap.class, null))
        .thenThrow(new NoEventSourceForClassException(ConfigMap.class));
    when(mockController.workflowContainsDependentForType(ConfigMap.class)).thenReturn(true);

    final var res = context.getSecondaryResource(ConfigMap.class, null, "cm-foo", "ns");

    assertThat(res).isEmpty();
  }

  @Test
  void getSecondaryResourceByNameUsesPrimaryNamespace() {
    final var primaryNamespace = "primary-ns";
    final var namespacedPrimary =
        new SecretBuilder()
            .withNewMetadata()
            .withName("primary")
            .withNamespace(primaryNamespace)
            .endMetadata()
            .build();
    final DefaultContext<HasMetadata> namespacedContext =
        new DefaultContext<>(null, mockController, namespacedPrimary, false, false);

    final var cm =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName("cm-foo")
            .withNamespace(primaryNamespace)
            .endMetadata()
            .build();

    final ManagedInformerEventSource<ConfigMap, HasMetadata, ?> cachingEventSource = mock();
    when(cachingEventSource.get(new ResourceID("cm-foo", primaryNamespace)))
        .thenReturn(Optional.of(cm));
    when(mockManager.getEventSourceFor(ConfigMap.class, "es-name")).thenReturn(cachingEventSource);

    final var res = namespacedContext.getSecondaryResource(ConfigMap.class, "es-name", "cm-foo");

    assertThat(res).contains(cm);
  }

  @Test
  void getSecondaryResourcesAsStreamByEventSourceUsesResourceCacheFastPath() {
    final var primaryNamespace = "primary-ns";
    final var namespacedPrimary =
        new SecretBuilder()
            .withNewMetadata()
            .withName("primary")
            .withNamespace(primaryNamespace)
            .endMetadata()
            .build();
    final DefaultContext<HasMetadata> namespacedContext =
        new DefaultContext<>(null, mockController, namespacedPrimary, false, false);

    final var cm1 =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName("cm-1")
            .withNamespace(primaryNamespace)
            .endMetadata()
            .build();
    final var cm2 =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName("cm-2")
            .withNamespace(primaryNamespace)
            .endMetadata()
            .build();

    final ManagedInformerEventSource<ConfigMap, HasMetadata, ?> resourceCacheEventSource = mock();
    when(resourceCacheEventSource.list(primaryNamespace)).thenReturn(Stream.of(cm1, cm2));
    when(mockManager.getEventSourceFor(ConfigMap.class, "es-name"))
        .thenReturn(resourceCacheEventSource);

    final var res =
        namespacedContext.getSecondaryResourcesAsStream(ConfigMap.class, "es-name").toList();

    assertThat(res).containsExactlyInAnyOrder(cm1, cm2);
    verify(resourceCacheEventSource).list(primaryNamespace);
  }

  @Test
  void getSecondaryResourcesAsStreamByEventSourceFastPathOnClusterScopedPrimary() {
    // cluster-scoped primary: has metadata but no namespace set.
    final var clusterScopedPrimary =
        new SecretBuilder().withNewMetadata().withName("primary").endMetadata().build();
    final DefaultContext<HasMetadata> clusterScopedContext =
        new DefaultContext<>(null, mockController, clusterScopedPrimary, false, false);

    final var cm1 = new ConfigMapBuilder().withNewMetadata().withName("cm-1").endMetadata().build();

    final ManagedInformerEventSource<ConfigMap, HasMetadata, ?> resourceCacheEventSource = mock();
    when(resourceCacheEventSource.list()).thenReturn(Stream.of(cm1));
    when(mockManager.getEventSourceFor(ConfigMap.class, "es-name"))
        .thenReturn(resourceCacheEventSource);

    final var res =
        clusterScopedContext.getSecondaryResourcesAsStream(ConfigMap.class, "es-name").toList();

    assertThat(res).containsExactly(cm1);
    verify(resourceCacheEventSource).list();
    verify(resourceCacheEventSource, never()).list(any(String.class));
  }

  @Test
  void getSecondaryResourcesAsStreamByEventSourceFallsBackToGetSecondaryResources() {
    final var cm1 =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName("cm-1")
            .withNamespace("ns")
            .endMetadata()
            .build();

    final EventSource<ConfigMap, HasMetadata> nonCacheEventSource = mock();
    when(nonCacheEventSource.getSecondaryResources(any())).thenReturn(Set.of(cm1));
    when(mockManager.getEventSourceFor(ConfigMap.class, "es-name")).thenReturn(nonCacheEventSource);

    final var res = context.getSecondaryResourcesAsStream(ConfigMap.class, "es-name").toList();

    assertThat(res).containsExactly(cm1);
  }

  @Test
  void getSecondaryResourcesAsStreamByEventSourceRethrowsWhenNotWorkflowManaged() {
    when(mockManager.getEventSourceFor(ConfigMap.class, "es-name"))
        .thenThrow(new NoEventSourceForClassException(ConfigMap.class));

    assertThatThrownBy(() -> context.getSecondaryResourcesAsStream(ConfigMap.class, "es-name"))
        .isInstanceOf(NoEventSourceForClassException.class);
  }

  @Test
  void getSecondaryResourcesAsStreamByEventSourceReturnsEmptyWhenWorkflowManaged() {
    when(mockManager.getEventSourceFor(ConfigMap.class, null))
        .thenThrow(new NoEventSourceForClassException(ConfigMap.class));
    when(mockController.workflowContainsDependentForType(ConfigMap.class)).thenReturn(true);

    final var res = context.getSecondaryResourcesAsStream(ConfigMap.class, null).toList();

    assertThat(res).isEmpty();
  }

  @Test
  void setRetryInfo() {
    RetryInfo retryInfo = mock();
    var newContext = context.setRetryInfo(retryInfo);
    assertThat(newContext).isSameAs(context);
    assertThat(newContext.getRetryInfo()).hasValue(retryInfo);
  }

  @Test
  void latestDistinctKeepsOnlyLatestResourceVersion() {
    // Create multiple resources with same name and namespace but different versions
    var pod1v1 = podWithNameAndVersion("pod1", "100");
    var pod1v2 = podWithNameAndVersion("pod1", "200");
    var pod1v3 = podWithNameAndVersion("pod1", "150");

    // Create a resource with different name
    var pod2v1 = podWithNameAndVersion("pod2", "100");

    // Create a resource with same name but different namespace
    var pod1OtherNsv1 = podWithNameAndVersion("pod1", "50", "other");

    setUpEventSourceWith(pod1v1, pod1v2, pod1v3, pod1OtherNsv1, pod2v1);

    var result = context.getSecondaryResourcesAsStream(Pod.class, true).toList();

    // Should have 3 resources: pod1 in default (latest version 200), pod2 in default, and pod1 in
    // other
    assertThat(result).hasSize(3);

    // Find pod1 in default namespace - should have version 200
    final var pod1InDefault =
        result.stream()
            .filter(r -> ResourceID.fromResource(r).isSameResource("pod1", "default"))
            .findFirst()
            .orElseThrow();
    assertThat(pod1InDefault.getMetadata().getResourceVersion()).isEqualTo("200");

    // Find pod2 in default namespace - should exist
    HasMetadata pod2InDefault =
        result.stream()
            .filter(r -> ResourceID.fromResource(r).isSameResource("pod2", "default"))
            .findFirst()
            .orElseThrow();
    assertThat(pod2InDefault.getMetadata().getResourceVersion()).isEqualTo("100");

    // Find pod1 in other namespace - should exist
    HasMetadata pod1InOther =
        result.stream()
            .filter(r -> ResourceID.fromResource(r).isSameResource("pod1", "other"))
            .findFirst()
            .orElseThrow();
    assertThat(pod1InOther.getMetadata().getResourceVersion()).isEqualTo("50");
  }

  private void setUpEventSourceWith(Pod... pods) {
    EventSource<Pod, HasMetadata> mockEventSource = mock();
    when(mockEventSource.getSecondaryResources(any())).thenReturn(Set.of(pods));
    when(mockManager.getEventSourcesFor(Pod.class)).thenReturn(List.of(mockEventSource));
  }

  private static Pod podWithNameAndVersion(
      String name, String resourceVersion, String... namespace) {
    final var ns = namespace != null && namespace.length > 0 ? namespace[0] : "default";
    return new PodBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(ns)
                .withResourceVersion(resourceVersion)
                .build())
        .build();
  }

  @Test
  void latestDistinctHandlesEmptyStream() {
    var result = context.getSecondaryResourcesAsStream(Pod.class, true).toList();

    assertThat(result).isEmpty();
  }

  @Test
  void latestDistinctHandlesSingleResource() {
    final var pod = podWithNameAndVersion("pod1", "100");
    setUpEventSourceWith(pod);

    var result = context.getSecondaryResourcesAsStream(Pod.class, true).toList();

    assertThat(result).hasSize(1);
    assertThat(result).contains(pod);
  }

  @Test
  void latestDistinctComparesNumericVersionsCorrectly() {
    // Test that version 1000 is greater than version 999 (not lexicographic)
    final var podV999 = podWithNameAndVersion("pod1", "999");
    final var podV1000 = podWithNameAndVersion("pod1", "1000");
    setUpEventSourceWith(podV999, podV1000);

    var result = context.getSecondaryResourcesAsStream(Pod.class, true).toList();

    assertThat(result).hasSize(1);
    HasMetadata resultPod = result.iterator().next();
    assertThat(resultPod.getMetadata().getResourceVersion()).isEqualTo("1000");
  }
}
