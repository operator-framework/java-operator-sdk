package io.javaoperatorsdk.operator.processing.event;

import io.javaoperatorsdk.operator.OperatorException;

public class NoEventSourceForClassException extends OperatorException {

  private Class<?> clazz;

  public NoEventSourceForClassException(Class<?> clazz) {
    this.clazz = clazz;
  }

  public Class<?> getClazz() {
    return clazz;
  }
}
