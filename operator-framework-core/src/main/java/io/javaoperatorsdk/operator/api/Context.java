package io.javaoperatorsdk.operator.api;

import java.util.Optional;

public interface Context {

    Optional<RetryInfo> getRetryInfo();

}
