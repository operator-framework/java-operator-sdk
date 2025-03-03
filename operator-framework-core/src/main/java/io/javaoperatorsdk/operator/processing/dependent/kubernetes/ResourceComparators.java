package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;

public class ResourceComparators {

  public static boolean compareConfigMapData(ConfigMap c1, ConfigMap c2) {
    return Objects.equals(c1.getData(), c2.getData())
        && Objects.equals(c1.getBinaryData(), c2.getBinaryData());
  }

  public static boolean compareSecretData(Secret s1, Secret s2) {
    return Objects.equals(s1.getType(), s2.getType())
        && Objects.equals(s1.getData(), s2.getData())
        && Objects.equals(s1.getStringData(), s2.getStringData());
  }
}
