package io.javaoperatorsdk.operator.api.reconciler.expectation;

public class ExpectationResult {

    private ExpectationStatus status;

    public ExpectationResult(ExpectationStatus status) {
        this.status = status;
    }

    public ExpectationStatus getStatus() {
        return status;
    }
}
