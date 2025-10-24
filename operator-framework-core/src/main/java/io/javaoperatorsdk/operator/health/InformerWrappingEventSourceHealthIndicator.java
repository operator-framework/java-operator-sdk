package io.javaoperatorsdk.operator.health;

import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface InformerWrappingEventSourceHealthIndicator<R extends HasMetadata>
    extends EventSourceHealthIndicator {

  Map<String, InformerHealthIndicator> informerHealthIndicators();

  @Override
  default Status getStatus() {
    var hasNonHealthy =
        informerHealthIndicators().values().stream().anyMatch(i -> i.getStatus() != Status.HEALTHY);
    return hasNonHealthy ? Status.UNHEALTHY : Status.HEALTHY;
  }
}
