package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.Cloner;

public class ResourceUpdatePreProcessor<R extends HasMetadata> {

  private final Cloner cloner;

  public ResourceUpdatePreProcessor(Cloner cloner) {
    this.cloner = cloner;
  }

  public R replaceSpecOnActual(R actual, R desired) {
    var clonedActual = cloner.clone(actual);
    if (desired instanceof ConfigMap) {
      ((ConfigMap) clonedActual).setData(((ConfigMap) desired).getData());
      ((ConfigMap) clonedActual).setBinaryData((((ConfigMap) desired).getBinaryData()));
      return clonedActual;
    } else if (desired instanceof Secret) {
      ((Secret) clonedActual).setData(((Secret) desired).getData());
      ((Secret) clonedActual).setStringData(((Secret) desired).getStringData());
      return clonedActual;
    } else {
      var desiredSpec = ReconcilerUtils.getSpec(desired);
      ReconcilerUtils.setSpec(clonedActual, desiredSpec);
      return clonedActual;
    }
  }
}
