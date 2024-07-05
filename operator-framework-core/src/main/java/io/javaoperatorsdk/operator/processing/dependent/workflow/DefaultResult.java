package io.javaoperatorsdk.operator.processing.dependent.workflow;

public class DefaultResult<T> implements ResultCondition.Result<T> {
  private final T result;
  private final boolean success;

  public DefaultResult(boolean success, T result) {
    this.result = result;
    this.success = success;
  }

  @Override
  public T getResult() {
    return result;
  }

  @Override
  public boolean isSuccess() {
    return success;
  }

  @Override
  public String toString() {
    return asString();
  }
}
