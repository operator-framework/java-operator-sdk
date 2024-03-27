package io.javaoperatorsdk.operator.processing.expiration;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.processing.retry.GenericRetry;

import static org.assertj.core.api.Assertions.assertThat;

public class RetryExpirationTestExecution {

  public static final int INITIAL_INTERVAL = 25;
  public static final int INITIAL_INTERVAL_PLUS_SLACK = INITIAL_INTERVAL + 10;

  RetryExpirationExecution expiration = new RetryExpirationExecution(new GenericRetry()
      .setInitialInterval(INITIAL_INTERVAL)
      .setMaxAttempts(2)
      .initExecution());

  @Test
  public void byDefaultExpired() {
    assertThat(expiration.isExpired()).isTrue();
  }

  @Test
  public void expiresAfterTime() throws InterruptedException {
    expiration.refreshed();
    assertThat(expiration.isExpired()).isFalse();

    Thread.sleep(INITIAL_INTERVAL_PLUS_SLACK);
    assertThat(expiration.isExpired()).isTrue();
  }

  @Test
  public void refreshResetsExpiration() throws InterruptedException {
    expiration.refreshed();
    Thread.sleep(INITIAL_INTERVAL_PLUS_SLACK);
    assertThat(expiration.isExpired()).isTrue();

    expiration.refreshed();

    assertThat(expiration.isExpired()).isFalse();
  }

  @Test
  public void notExpiresAfterMaxAttempt() throws InterruptedException {
    expiration.refreshed();
    expiration.refreshed();
    expiration.refreshed();

    Thread.sleep(INITIAL_INTERVAL_PLUS_SLACK);

    assertThat(expiration.isExpired()).isFalse();
  }

}
