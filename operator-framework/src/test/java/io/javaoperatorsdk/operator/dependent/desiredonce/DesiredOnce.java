package io.javaoperatorsdk.operator.dependent.desiredonce;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("io.josdk")
@Version("v1")
public class DesiredOnce extends CustomResource<DesiredOnceSpec, Void> {
  static final String KEY = "key";
  static final String VALUE = "value";

  public DesiredOnce() {}

  public DesiredOnce(String value) {
    this.spec = new DesiredOnceSpec(value);
  }
}
