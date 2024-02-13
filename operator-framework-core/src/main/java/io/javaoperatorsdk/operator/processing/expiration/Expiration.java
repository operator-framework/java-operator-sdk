package io.javaoperatorsdk.operator.processing.expiration;

public interface Expiration {

  boolean isExpired();

  void refreshed();

}
