package io.javaoperatorsdk.operator.api;

import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;

public interface Context<T extends CustomResource> {

  Optional<RetryInfo> getRetryInfo();

}
