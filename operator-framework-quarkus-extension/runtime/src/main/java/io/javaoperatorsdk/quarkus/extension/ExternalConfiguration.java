package io.javaoperatorsdk.quarkus.extension;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import java.util.Map;

@ConfigRoot(name = "operator-sdk", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class ExternalConfiguration {

  /** Maps a controller name to its configuration. */
  @ConfigItem public Map<String, ExternalControllerConfiguration> controllers;
}
