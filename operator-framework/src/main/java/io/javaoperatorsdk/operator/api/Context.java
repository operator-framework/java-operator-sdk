package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;

public interface Context<T extends CustomResource> {

    RetryInfo retryInfo();

}
