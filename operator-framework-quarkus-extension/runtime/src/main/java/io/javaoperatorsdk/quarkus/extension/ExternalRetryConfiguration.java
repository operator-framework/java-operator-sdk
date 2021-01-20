package io.javaoperatorsdk.quarkus.extension;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;
import java.util.Optional;

@ConfigGroup
public class ExternalRetryConfiguration {

  /** How many times an operation should be retried before giving up */
  @ConfigItem public Optional<Integer> maxAttempts;

  /** The configuration of the retry interval. */
  @ConfigItem public Optional<ExternalIntervalConfiguration> interval;
}
