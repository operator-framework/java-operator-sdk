package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.OperatorException;

public abstract class LifecycleAwareEventSource extends AbstractEventSource {

  private volatile boolean started = false;

  public boolean isStarted() {
    return started;
  }

  public LifecycleAwareEventSource setStarted(boolean started) {
    this.started = started;
    return this;
  }

  @Override
  public void start() throws OperatorException {
    started = true;
  }

  @Override
  public void stop() throws OperatorException {
    started = false;
  }
}
