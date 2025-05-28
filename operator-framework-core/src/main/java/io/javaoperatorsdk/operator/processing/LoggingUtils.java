package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;

public class LoggingUtils {

  private LoggingUtils() {}

  public static boolean isNotSensitiveResource(HasMetadata resource) {
    return !(resource instanceof Secret);
  }
}
