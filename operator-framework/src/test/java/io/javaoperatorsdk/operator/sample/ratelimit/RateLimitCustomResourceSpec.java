package io.javaoperatorsdk.operator.sample.ratelimit;

public class RateLimitCustomResourceSpec {

  private int number;

  public int getNumber() {
    return number;
  }

  public RateLimitCustomResourceSpec setNumber(int number) {
    this.number = number;
    return this;
  }
}
