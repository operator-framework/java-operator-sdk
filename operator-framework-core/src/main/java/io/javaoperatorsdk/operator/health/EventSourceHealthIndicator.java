package io.javaoperatorsdk.operator.health;

public interface EventSourceHealthIndicator {

  /**
   * Retrieves the health status of an {@link
   * io.javaoperatorsdk.operator.processing.event.source.EventSource}
   *
   * @return the health status
   * @see Status
   */
  Status getStatus();
}
