package com.github.containersolutions.operator.api;

public class RetryInfo {

    private int retryNumber;
    private boolean lastAttempt;

    public RetryInfo(int retryNumber, boolean lastAttempt) {
        this.retryNumber = retryNumber;
        this.lastAttempt = lastAttempt;
    }

    public int getRetryNumber() {
        return retryNumber;
    }

    public boolean isLastAttempt() {
        return lastAttempt;
    }
}
