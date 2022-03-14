package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

public class ReconcileResult<R> {

  private R resource;
  private Operation operation;

  public static <T> ReconcileResult<T> resourceCreated(T resource) {
    return new ReconcileResult<>(resource, Operation.CREATED);
  }

  public static <T> ReconcileResult<T> resourceUpdated(T resource) {
    return new ReconcileResult<>(resource, Operation.UPDATED);
  }

  public static <T> ReconcileResult<T> noOperation(T resource) {
    return new ReconcileResult<>(resource, Operation.NONE);
  }

  private ReconcileResult(R resource, Operation operation) {
    this.resource = resource;
    this.operation = operation;
  }

  public Optional<R> getResource() {
    return Optional.ofNullable(resource);
  }

  public Operation getOperation() {
    return operation;
  }

  public enum Operation {
    CREATED, UPDATED, NONE
  }
}
