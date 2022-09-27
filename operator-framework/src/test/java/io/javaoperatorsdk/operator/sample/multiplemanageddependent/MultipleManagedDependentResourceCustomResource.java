package io.javaoperatorsdk.operator.sample.multiplemanageddependent;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("mdr")
public class MultipleManagedDependentResourceCustomResource
    extends CustomResource<Void, Void>
    implements Namespaced {

  public String getConfigMapName(int id) {
    return "configmap" + id;
  }
}
