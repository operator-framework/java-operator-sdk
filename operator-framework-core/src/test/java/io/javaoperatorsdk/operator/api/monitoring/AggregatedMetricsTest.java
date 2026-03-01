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
package io.javaoperatorsdk.operator.api.monitoring;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AggregatedMetricsTest {

  private final Metrics metrics1 = mock();
  private final Metrics metrics2 = mock();
  private final Metrics metrics3 = mock();
  private final Controller<HasMetadata> controller = mock();
  private final Event event = mock();
  private final HasMetadata resource = mock();
  private final RetryInfo retryInfo = mock();
  private final ResourceID resourceID = mock();
  private final Metrics.ControllerExecution<String> controllerExecution = mock();

  private final Map<String, Object> metadata = Map.of("kind", "TestResource");
  private final AggregatedMetrics aggregatedMetrics =
      new AggregatedMetrics(List.of(metrics1, metrics2, metrics3));

  @Test
  void constructor_shouldThrowNullPointerExceptionWhenMetricsListIsNull() {
    assertThatThrownBy(() -> new AggregatedMetrics(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("metricsList must not be null");
  }

  @Test
  void constructor_shouldThrowIllegalArgumentExceptionWhenMetricsListIsEmpty() {
    assertThatThrownBy(() -> new AggregatedMetrics(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("metricsList must contain at least one Metrics instance");
  }

  @Test
  void controllerRegistered_shouldDelegateToAllMetricsInOrder() {
    aggregatedMetrics.controllerRegistered(controller);

    final var inOrder = inOrder(metrics1, metrics2, metrics3);
    inOrder.verify(metrics1).controllerRegistered(controller);
    inOrder.verify(metrics2).controllerRegistered(controller);
    inOrder.verify(metrics3).controllerRegistered(controller);
    verifyNoMoreInteractions(metrics1, metrics2, metrics3);
  }

  @Test
  void receivedEvent_shouldDelegateToAllMetricsInOrder() {
    aggregatedMetrics.receivedEvent(event, metadata);

    final var inOrder = inOrder(metrics1, metrics2, metrics3);
    inOrder.verify(metrics1).receivedEvent(event, metadata);
    inOrder.verify(metrics2).receivedEvent(event, metadata);
    inOrder.verify(metrics3).receivedEvent(event, metadata);
    verifyNoMoreInteractions(metrics1, metrics2, metrics3);
  }

  @Test
  void reconcileCustomResource_shouldDelegateToAllMetricsInOrder() {
    aggregatedMetrics.reconcileCustomResource(resource, retryInfo, metadata);

    final var inOrder = inOrder(metrics1, metrics2, metrics3);
    inOrder.verify(metrics1).reconcileCustomResource(resource, retryInfo, metadata);
    inOrder.verify(metrics2).reconcileCustomResource(resource, retryInfo, metadata);
    inOrder.verify(metrics3).reconcileCustomResource(resource, retryInfo, metadata);
    verifyNoMoreInteractions(metrics1, metrics2, metrics3);
  }

  @Test
  void failedReconciliation_shouldDelegateToAllMetricsInOrder() {
    final var exception = new RuntimeException("Test exception");
    aggregatedMetrics.failedReconciliation(resource, retryInfo, exception, metadata);

    final var inOrder = inOrder(metrics1, metrics2, metrics3);
    inOrder.verify(metrics1).failedReconciliation(resource, retryInfo, exception, metadata);
    inOrder.verify(metrics2).failedReconciliation(resource, retryInfo, exception, metadata);
    inOrder.verify(metrics3).failedReconciliation(resource, retryInfo, exception, metadata);
    verifyNoMoreInteractions(metrics1, metrics2, metrics3);
  }

  @Test
  void reconciliationExecutionStarted_shouldDelegateToAllMetricsInOrder() {
    aggregatedMetrics.reconciliationExecutionStarted(resource, metadata);

    final var inOrder = inOrder(metrics1, metrics2, metrics3);
    inOrder.verify(metrics1).reconciliationExecutionStarted(resource, metadata);
    inOrder.verify(metrics2).reconciliationExecutionStarted(resource, metadata);
    inOrder.verify(metrics3).reconciliationExecutionStarted(resource, metadata);
    verifyNoMoreInteractions(metrics1, metrics2, metrics3);
  }

  @Test
  void reconciliationExecutionFinished_shouldDelegateToAllMetricsInOrder() {
    aggregatedMetrics.reconciliationExecutionFinished(resource, retryInfo, metadata);

    final var inOrder = inOrder(metrics1, metrics2, metrics3);
    inOrder.verify(metrics1).reconciliationExecutionFinished(resource, retryInfo, metadata);
    inOrder.verify(metrics2).reconciliationExecutionFinished(resource, retryInfo, metadata);
    inOrder.verify(metrics3).reconciliationExecutionFinished(resource, retryInfo, metadata);
    verifyNoMoreInteractions(metrics1, metrics2, metrics3);
  }

  @Test
  void cleanupDoneFor_shouldDelegateToAllMetricsInOrder() {
    aggregatedMetrics.cleanupDoneFor(resourceID, metadata);

    final var inOrder = inOrder(metrics1, metrics2, metrics3);
    inOrder.verify(metrics1).cleanupDoneFor(resourceID, metadata);
    inOrder.verify(metrics2).cleanupDoneFor(resourceID, metadata);
    inOrder.verify(metrics3).cleanupDoneFor(resourceID, metadata);
    verifyNoMoreInteractions(metrics1, metrics2, metrics3);
  }

  @Test
  void successfullyFinishedReconciliation_shouldDelegateToAllMetricsInOrder() {
    aggregatedMetrics.successfullyFinishedReconciliation(resource, metadata);

    final var inOrder = inOrder(metrics1, metrics2, metrics3);
    inOrder.verify(metrics1).successfullyFinishedReconciliation(resource, metadata);
    inOrder.verify(metrics2).successfullyFinishedReconciliation(resource, metadata);
    inOrder.verify(metrics3).successfullyFinishedReconciliation(resource, metadata);
  }

  @Test
  void timeControllerExecution_shouldOnlyDelegateToFirstMetrics() throws Exception {
    final var expectedResult = "execution result";
    when(metrics1.timeControllerExecution(controllerExecution)).thenReturn(expectedResult);

    final var result = aggregatedMetrics.timeControllerExecution(controllerExecution);

    assertThat(result).isEqualTo(expectedResult);
    verify(metrics1).timeControllerExecution(controllerExecution);
    verify(metrics2, never()).timeControllerExecution(any());
    verify(metrics3, never()).timeControllerExecution(any());
    verifyNoMoreInteractions(metrics1, metrics2, metrics3);
  }

  @Test
  void timeControllerExecution_shouldPropagateException() throws Exception {
    final var expectedException = new RuntimeException("Controller execution failed");
    when(metrics1.timeControllerExecution(controllerExecution)).thenThrow(expectedException);

    assertThatThrownBy(() -> aggregatedMetrics.timeControllerExecution(controllerExecution))
        .isSameAs(expectedException);

    verify(metrics1).timeControllerExecution(controllerExecution);
    verify(metrics2, never()).timeControllerExecution(any());
    verify(metrics3, never()).timeControllerExecution(any());
    verifyNoMoreInteractions(metrics1, metrics2, metrics3);
  }

  @Test
  void monitorSizeOf_shouldDelegateToAllMetricsInOrderAndReturnOriginalMap() {
    final var testMap = Map.of("key1", "value1");
    final var mapName = "testMap";

    final var result = aggregatedMetrics.monitorSizeOf(testMap, mapName);

    assertThat(result).isSameAs(testMap);
    verify(metrics1).monitorSizeOf(testMap, mapName);
    verify(metrics2).monitorSizeOf(testMap, mapName);
    verify(metrics3).monitorSizeOf(testMap, mapName);
    verifyNoMoreInteractions(metrics1, metrics2, metrics3);
  }
}
