package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.*;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ReconcileResult<R> {

  private final Map<R, Operation> resourceOperations;

  public static <T> ReconcileResult<T> resourceCreated(T resource) {
    return new ReconcileResult<>(resource, Operation.CREATED);
  }

  public static <T> ReconcileResult<T> resourceUpdated(T resource) {
    return new ReconcileResult<>(resource, Operation.UPDATED);
  }

  public static <T> ReconcileResult<T> noOperation(T resource) {
    return new ReconcileResult<>(resource, Operation.NONE);
  }

  @SafeVarargs
  public static <T> ReconcileResult<T> aggregatedResult(ReconcileResult<T>... results) {
    if (results == null) {
      throw new IllegalArgumentException("Should provide results to aggregate");
    }
    if (results.length == 1) {
      return results[0];
    }
    final Map<T, Operation> operations = new HashMap<>(results.length);
    for (ReconcileResult<T> res : results) {
      res.getSingleResource().ifPresent(r -> operations.put(r, res.getSingleOperation()));
    }
    return new ReconcileResult<>(operations);
  }

  @Override
  public String toString() {
    return resourceOperations.entrySet().stream().collect(Collectors.toMap(
        e -> e instanceof HasMetadata ? ResourceID.fromResource((HasMetadata) e) : e,
        Map.Entry::getValue))
        .toString();
  }

  private ReconcileResult(R resource, Operation operation) {
    resourceOperations = Map.of(resource, operation);
  }

  private ReconcileResult(Map<R, Operation> operations) {
    resourceOperations = Collections.unmodifiableMap(operations);
  }

  public Optional<R> getSingleResource() {
    return resourceOperations.entrySet().stream().findFirst().map(Map.Entry::getKey);
  }

  public Operation getSingleOperation() {
    return resourceOperations.entrySet().stream().findFirst().map(Map.Entry::getValue)
        .orElseThrow();
  }

  @SuppressWarnings("unused")
  public Map<R, Operation> getResourceOperations() {
    return resourceOperations;
  }

  public enum Operation {
    CREATED, UPDATED, NONE
  }
}
