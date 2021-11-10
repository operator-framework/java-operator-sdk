package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

public interface Context {

  Optional<RetryInfo> getRetryInfo();

}
