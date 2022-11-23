package io.javaoperatorsdk.operator.health;

public enum Status {

  HEALTHY, UNHEALTHY,
  /**
   * For event sources where it cannot be determined if it is healthy ot not.
   */
  UNKNOWN

}
