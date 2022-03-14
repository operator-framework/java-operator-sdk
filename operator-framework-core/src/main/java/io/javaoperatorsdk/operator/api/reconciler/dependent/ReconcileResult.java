package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

public class ReconcileResult<R> {

    private R resource;
    private Operation operation;

    public ReconcileResult(R resource, Operation operation) {
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
        CREATED,
        UPDATED,
        NONE
    }
}
