package io.javaoperatorsdk.operator.baseapi.filter;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("ftc")
public class FilterTestCustomResource
    extends CustomResource<FilterTestResourceSpec, FilterTestResourceStatus> implements Namespaced {

  public String getConfigMapName(int id) {
    return "configmap" + id;
  }
}
