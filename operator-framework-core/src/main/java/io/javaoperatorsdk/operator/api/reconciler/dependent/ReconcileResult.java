package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.*;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ReconcileResult<R> {

  private Map<R, Operation> resourceOperations = new HashMap<>(1);

  public ReconcileResult() {}

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
    return resourceOperations.entrySet().stream().collect(Collectors.toMap(
        e -> e instanceof HasMetadata ? ResourceID.fromResource((HasMetadata) e) : e,
        Map.Entry::getValue))
        .toString();
  }

  private ReconcileResult(R resource, Operation operation) {
    resourceOperations.put(resource, operation);
  }

  public Optional<R> getSingleResource() {
    return resourceOperations.entrySet().stream().findFirst().map(Map.Entry::getKey);
  }

  public Operation getSingleOperation() {
    return resourceOperations.entrySet().stream().findFirst().map(Map.Entry::getValue)
        .orElseThrow();
  }

  public Map<R, Operation> getResourceOperations() {
    return resourceOperations;
  }

  public void addReconcileResult(ReconcileResult<R> result) {
    result.getSingleResource().ifPresent(r -> {
      resourceOperations.put(r, result.getSingleOperation());
    });

  }

  public enum Operation {
    CREATED, UPDATED, NONE
  }
}
