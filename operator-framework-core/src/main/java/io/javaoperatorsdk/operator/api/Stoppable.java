package io.javaoperatorsdk.operator.api;

import io.javaoperatorsdk.operator.OperatorException;

public interface Stoppable {
  void start() throws OperatorException;

  void stop() throws OperatorException;
}
