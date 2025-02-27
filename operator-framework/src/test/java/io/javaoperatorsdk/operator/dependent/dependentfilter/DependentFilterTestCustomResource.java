package io.javaoperatorsdk.operator.dependent.dependentfilter;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("dft")
public class DependentFilterTestCustomResource
    extends CustomResource<DependentFilterTestResourceSpec, Void> implements Namespaced {

  public String getConfigMapName(int id) {
    return "configmap" + id;
  }
}
