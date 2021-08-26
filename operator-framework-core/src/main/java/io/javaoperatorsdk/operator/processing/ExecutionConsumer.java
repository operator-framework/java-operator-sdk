package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.client.CustomResource;

class ExecutionConsumer<R extends CustomResource<?, ?>> implements Runnable {

  private final ExecutionScope<R> executionScope;
  private final EventDispatcher<R> eventDispatcher;
  private final DefaultEventHandler<R> defaultEventHandler;

  ExecutionConsumer(
      ExecutionScope<R> executionScope,
      EventDispatcher<R> eventDispatcher,
      DefaultEventHandler<R> defaultEventHandler) {
    this.executionScope = executionScope;
    this.eventDispatcher = eventDispatcher;
    this.defaultEventHandler = defaultEventHandler;
  }

  @Override
  public void run() {
    PostExecutionControl<R> postExecutionControl = eventDispatcher.handleExecution(executionScope);
    defaultEventHandler.eventProcessingFinished(executionScope, postExecutionControl);
  }
}
