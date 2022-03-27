package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ReconcileResult<R> {

  private final R resource;
  private final Operation operation;

  public static <T> ReconcileResult<T> resourceCreated(T resource) {
    return new ReconcileResult<>(resource, Operation.CREATED);
  }

  public static <T> ReconcileResult<T> resourceUpdated(T resource) {
    return new ReconcileResult<>(resource, Operation.UPDATED);
  }

  public static <T> ReconcileResult<T> noOperation(T resource) {
    return new ReconcileResult<>(resource, Operation.NONE);
  }

  @Override
  public String toString() {
    return getResource()
        .map(r -> r instanceof HasMetadata ? ResourceID.fromResource((HasMetadata) r) : r)
        .orElse("no resource")
        + " -> " + operation;
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
