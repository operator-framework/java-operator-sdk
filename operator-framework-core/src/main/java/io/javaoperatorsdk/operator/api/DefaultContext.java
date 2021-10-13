package io.javaoperatorsdk.operator.api;

import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;

public class DefaultContext<T extends CustomResource> implements Context<T> {

  private final RetryInfo retryInfo;

  public DefaultContext(RetryInfo retryInfo) {
    this.retryInfo = retryInfo;
  }

  @Override
  public Optional<RetryInfo> getRetryInfo() {
    return Optional.ofNullable(retryInfo);
  }
}
