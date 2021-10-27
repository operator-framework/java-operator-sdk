package io.javaoperatorsdk.operator.api;

import java.util.Optional;

public class DefaultContext implements Context {

    private final RetryInfo retryInfo;

    public DefaultContext(RetryInfo retryInfo) {
        this.retryInfo = retryInfo;
    }

    @Override
    public Optional<RetryInfo> getRetryInfo() {
        return Optional.ofNullable(retryInfo);
  }
}
