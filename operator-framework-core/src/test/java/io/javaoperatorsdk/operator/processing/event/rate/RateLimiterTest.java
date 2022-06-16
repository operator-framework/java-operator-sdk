package io.javaoperatorsdk.operator.processing.event.rate;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

  public static final Duration REFRESH_PERIOD = Duration.ofSeconds(1);
  ResourceID resourceID = ResourceID.fromResource(TestUtils.testCustomResource());

  @Test
  void acquirePermissionForNewResource() {
    var rl = new RateLimiter(REFRESH_PERIOD, 1);
    var res = rl.acquirePermission(resourceID);

    assertThat(res).isEmpty();
  }

  @Test
  void returnsMinimalDurationToAcquirePermission() {
    var rl = new RateLimiter(REFRESH_PERIOD, 1);
    var res = rl.acquirePermission(resourceID);
    assertThat(res).isEmpty();

    res = rl.acquirePermission(resourceID);

    assertThat(res).isPresent();
    assertThat(res.get()).isLessThan(REFRESH_PERIOD);
  }

  @Test
  void resetsPeriodAfterLimit() throws InterruptedException {
    var rl = new RateLimiter(REFRESH_PERIOD, 1);
    var res = rl.acquirePermission(resourceID);
    assertThat(res).isEmpty();
    res = rl.acquirePermission(resourceID);
    assertThat(res).isPresent();

    // sleep plus some slack
    Thread.sleep(REFRESH_PERIOD.toMillis() + REFRESH_PERIOD.toMillis() / 2);

    res = rl.acquirePermission(resourceID);
    assertThat(res).isEmpty();
  }

}
