package io.javaoperatorsdk.operator.health;

public interface InformerHealthIndicator extends EventSourceHealthIndicator {

  boolean hasSynced();

  boolean isWatching();

  boolean isRunning();

  @Override
  Status getStatus();

  String getTargetNamespace();
}
