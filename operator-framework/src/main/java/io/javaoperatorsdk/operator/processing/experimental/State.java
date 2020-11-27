package io.javaoperatorsdk.operator.processing.experimental;

import java.util.Optional;

public class State<T> {

    private Status status;
    private T details;

    public State(Status status, T details) {
        this.status = status;
        this.details = details;
    }

    public State(Status status) {
        this(status,null);
    }

    public Status getStatus() {
        return status;
    }

    public Optional<T> getDetails() {
        return Optional.ofNullable(details);
    }
}
