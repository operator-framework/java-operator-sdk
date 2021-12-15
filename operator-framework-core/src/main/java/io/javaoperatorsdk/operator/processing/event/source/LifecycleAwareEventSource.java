package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;

public abstract class LifecycleAwareEventSource<P extends HasMetadata>
    extends AbstractEventSource<P> {

  private volatile boolean running = false;

  protected LifecycleAwareEventSource(Class<P> resourceClass) {
    super(resourceClass);
  }

  public boolean isRunning() {
    return running;
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
