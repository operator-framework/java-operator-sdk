package io.javaoperatorsdk.operator.api.monitoring;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * An aggregated implementation of the {@link Metrics} interface that delegates method calls to a
 * collection of {@link Metrics} instances using the composite pattern.
 *
 * <p>This class allows multiple metrics providers to be combined into a single metrics instance,
 * enabling simultaneous collection of metrics data by different monitoring systems or providers.
 * All method calls are delegated to each metrics instance in the list in the order they were
 * provided to the constructor.
 *
 * <p><strong>Important:</strong> The {@link #timeControllerExecution(ControllerExecution)} method
 * is handled specially - it is only invoked on the first metrics instance in the list, since it's
 * not an idempotent operation and can only be executed once. The controller execution cannot be
 * repeated multiple times as it would produce side effects and potentially inconsistent results.
 *
 * <p>All other methods are called on every metrics instance in the list, preserving the order of
 * execution as specified in the constructor.
 *
 * @see Metrics
 */
public final class AggregatedMetrics implements Metrics {

  private final List<Metrics> metricsList;

  /**
   * Creates a new AggregatedMetrics instance that will delegate method calls to the provided list
   * of metrics instances.
   *
   * @param metricsList the list of metrics instances to delegate to; must not be null and must
   *     contain at least one metrics instance
   * @throws NullPointerException if metricsList is null
   * @throws IllegalArgumentException if metricsList is empty
   */
  public AggregatedMetrics(List<Metrics> metricsList) {
    Objects.requireNonNull(metricsList, "metricsList must not be null");
    if (metricsList.isEmpty()) {
      throw new IllegalArgumentException("metricsList must contain at least one Metrics instance");
    }
    this.metricsList = List.copyOf(metricsList);
  }

  @Override
  public void controllerRegistered(Controller<? extends HasMetadata> controller) {
    metricsList.forEach(metrics -> metrics.controllerRegistered(controller));
  }

  @Override
  public void receivedEvent(Event event, Map<String, Object> metadata) {
    metricsList.forEach(metrics -> metrics.receivedEvent(event, metadata));
  }

  @Override
  public void reconcileCustomResource(
      HasMetadata resource, RetryInfo retryInfo, Map<String, Object> metadata) {
    metricsList.forEach(metrics -> metrics.reconcileCustomResource(resource, retryInfo, metadata));
  }

  @Override
  public void failedReconciliation(
      HasMetadata resource, Exception exception, Map<String, Object> metadata) {
    metricsList.forEach(metrics -> metrics.failedReconciliation(resource, exception, metadata));
  }

  @Override
  public void reconciliationExecutionStarted(HasMetadata resource, Map<String, Object> metadata) {
    metricsList.forEach(metrics -> metrics.reconciliationExecutionStarted(resource, metadata));
  }

  @Override
  public void reconciliationExecutionFinished(HasMetadata resource, Map<String, Object> metadata) {
    metricsList.forEach(metrics -> metrics.reconciliationExecutionFinished(resource, metadata));
  }

  @Override
  public void cleanupDoneFor(ResourceID resourceID, Map<String, Object> metadata) {
    metricsList.forEach(metrics -> metrics.cleanupDoneFor(resourceID, metadata));
  }

  @Override
  public void finishedReconciliation(HasMetadata resource, Map<String, Object> metadata) {
    metricsList.forEach(metrics -> metrics.finishedReconciliation(resource, metadata));
  }

  @Override
  public <T> T timeControllerExecution(ControllerExecution<T> execution) throws Exception {
    return metricsList.get(0).timeControllerExecution(execution);
  }

  @Override
  public <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    metricsList.forEach(metrics -> metrics.monitorSizeOf(map, name));
    return map;
  }
}
