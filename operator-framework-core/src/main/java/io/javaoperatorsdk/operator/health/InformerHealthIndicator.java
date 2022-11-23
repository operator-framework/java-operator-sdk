package io.javaoperatorsdk.operator.health;

public interface InformerHealthIndicator extends EventSourceHealthIndicator {

  boolean hasSynced();

  boolean isWatching();

  boolean isRunning();

  @Override
  default Status getStatus() {
    return isRunning() && hasSynced() && isWatching() ? Status.HEALTHY : Status.UNHEALTHY;
  }

  String getTargetNamespace();
}
