package io.javaoperatorsdk.operator.api;

import io.javaoperatorsdk.operator.OperatorException;

public interface LifecycleAware {
  void start() throws OperatorException;

  void stop() throws OperatorException;
}
