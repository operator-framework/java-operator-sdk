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
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.NoEventSourceForClassException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
            .filter(r -> ResourceID.fromResource(r).equals(new ResourceID("pod1", "default")))
            .findFirst()
            .orElseThrow();
    assertThat(pod1InDefault.getMetadata().getResourceVersion()).isEqualTo("200");

    // Find pod2 in default namespace - should exist
    HasMetadata pod2InDefault =
        result.stream()
            .filter(r -> ResourceID.fromResource(r).equals("pod2", "default"))
            .findFirst()
            .orElseThrow();
    assertThat(pod2InDefault.getMetadata().getResourceVersion()).isEqualTo("100");

    // Find pod1 in other namespace - should exist
    HasMetadata pod1InOther =
        result.stream()
            .filter(r -> ResourceID.fromResource(r).equals("pod1", "other"))
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
