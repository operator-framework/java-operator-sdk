package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ReconcileResult<R> {

  private final R resource;
  private final Operation operation;
  private final Exception error;

  public static <T> ReconcileResult<T> resourceCreated(T resource) {
    return new ReconcileResult<>(resource, Operation.CREATED, null);
  }

  public static <T> ReconcileResult<T> resourceUpdated(T resource) {
    return new ReconcileResult<>(resource, Operation.UPDATED, null);
  }

  public static <T> ReconcileResult<T> noOperation(T resource) {
    return new ReconcileResult<>(resource, Operation.NONE, null);
  }

  public static <T> ReconcileResult<T> error(T resource, Exception error) {
    return new ReconcileResult<>(resource, Operation.ERROR, error);
  }

  @Override
  public String toString() {
    return getResource()
        .map(r -> r instanceof HasMetadata ? ResourceID.fromResource((HasMetadata) r) : r)
        .orElse("no resource")
        + " -> " + operation
        + getError().map(e -> " (" + e.getMessage() + ")").orElse("");
  }

  private ReconcileResult(R resource, Operation operation, Exception error) {
    this.resource = resource;
    this.operation = operation;
    this.error = error;
  }

  public Optional<R> getResource() {
    return Optional.ofNullable(resource);
  }

  public Operation getOperation() {
    return operation;
  }

  public Optional<Exception> getError() {
    return Optional.ofNullable(error);
  }

  public enum Operation {
    CREATED, UPDATED, NONE, ERROR
  }
}
