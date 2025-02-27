package io.javaoperatorsdk.operator.health;

/**
 * The health status of an {@link io.javaoperatorsdk.operator.processing.event.source.EventSource}
 */
public enum Status {
  HEALTHY,
  UNHEALTHY,
  /** For event sources where it cannot be determined if it is healthy ot not. */
  UNKNOWN
}
