package io.javaoperatorsdk.operator.processing.expiration;

public interface ExpirationExecution {

  boolean isExpired();

  void refreshed();

}
