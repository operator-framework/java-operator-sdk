package io.javaoperatorsdk.operator.processing.retry;

import io.javaoperatorsdk.operator.api.config.RetryConfiguration;

public interface Retry extends RetryConfiguration {

  RetryExecution initExecution();
}
