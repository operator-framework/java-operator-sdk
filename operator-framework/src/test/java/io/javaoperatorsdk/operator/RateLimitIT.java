package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.ratelimit.RateLimitCustomResource;
import io.javaoperatorsdk.operator.sample.ratelimit.RateLimitCustomResourceSpec;
import io.javaoperatorsdk.operator.sample.ratelimit.RateLimitReconciler;

import static io.javaoperatorsdk.operator.sample.ratelimit.RateLimitReconciler.REFRESH_PERIOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RateLimitIT {

  private final static Logger log = LoggerFactory.getLogger(RateLimitIT.class);

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new RateLimitReconciler())
          .build();

  @Test
  void rateLimitsExecution() {
    var res = operator.create(createResource());
    IntStream.rangeClosed(1, 5).forEach(i -> {
      log.debug("replacing resource version: {}", i);
      var resource = createResource();
      resource.getSpec().setNumber(i);
      operator.replace(resource);
    });
    await().pollInterval(Duration.ofMillis(100))
        .pollDelay(Duration.ofMillis(REFRESH_PERIOD / 2))
        .untilAsserted(() -> assertThat(
            operator.getReconcilerOfType(RateLimitReconciler.class).getNumberOfExecutions())
            .isEqualTo(1));

    await().pollDelay(Duration.ofMillis(REFRESH_PERIOD))
        .untilAsserted(() -> assertThat(
            operator.getReconcilerOfType(RateLimitReconciler.class).getNumberOfExecutions())
            .isEqualTo(2));
  }

  public RateLimitCustomResource createResource() {
    RateLimitCustomResource res = new RateLimitCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName("test")
        .build());
    res.setSpec(new RateLimitCustomResourceSpec());
    res.getSpec().setNumber(0);
    return res;
  }

}
