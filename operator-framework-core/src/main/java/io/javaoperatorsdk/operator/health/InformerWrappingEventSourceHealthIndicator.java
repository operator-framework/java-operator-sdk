package io.javaoperatorsdk.operator.health;

import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface InformerWrappingEventSourceHealthIndicator<R extends HasMetadata>
    extends EventSourceHealthIndicator {

  Map<String, InformerHealthIndicator> informerHealthIndicators();

  @Override
  default Status getStatus() {
    var nonUp =
        informerHealthIndicators().values().stream()
            .filter(i -> i.getStatus() != Status.HEALTHY)
            .findAny();

    return nonUp.isPresent() ? Status.UNHEALTHY : Status.HEALTHY;
  }
}
