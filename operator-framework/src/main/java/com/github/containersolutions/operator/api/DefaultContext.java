package com.github.containersolutions.operator.api;

import io.fabric8.kubernetes.client.CustomResource;

public class DefaultContext<T extends CustomResource> implements Context<T> {

    private final RetryInfo retryInfo;

    public DefaultContext(RetryInfo retryInfo) {
        this.retryInfo = retryInfo;
    }

    @Override
    public RetryInfo retryInfo() {
        return retryInfo;
    }
}
