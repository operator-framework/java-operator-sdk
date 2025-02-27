package io.javaoperatorsdk.operator.sample.observedgeneration;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk.io")
@Version("v1")
public class ObservedGenCustomResource extends CustomResource<ObservedGenSpec, ObservedGenStatus> {

  @Override
  protected ObservedGenSpec initSpec() {
    return new ObservedGenSpec();
  }

  @Override
  protected ObservedGenStatus initStatus() {
    return new ObservedGenStatus();
  }
}
