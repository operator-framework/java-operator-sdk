package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.OperatorException;

public abstract class LifecycleAwareEventSource extends AbstractEventSource {

  private volatile boolean running = false;

  public boolean isRunning() {
    return running;
  }

  public LifecycleAwareEventSource setStarted(boolean running) {
    this.running = running;
    return this;
  }

  @Override
  public void start() throws OperatorException {
    running = true;
  }

  @Override
  public void stop() throws OperatorException {
    running = false;
  }
}
