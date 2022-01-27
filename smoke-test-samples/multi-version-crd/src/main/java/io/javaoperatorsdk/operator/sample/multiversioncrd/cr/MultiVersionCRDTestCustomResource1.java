package io.javaoperatorsdk.operator.sample.multiversioncrd.cr;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@Kind("MultiVersionCRDTestCustomResource")
@ShortNames("mv1")
public class MultiVersionCRDTestCustomResource1
    extends
    CustomResource<MultiVersionCRDTestCustomResourceSpec1, MultiVersionCRDTestCustomResourceStatus1>
    implements Namespaced {

  @Override
  protected MultiVersionCRDTestCustomResourceStatus1 initStatus() {
    return new MultiVersionCRDTestCustomResourceStatus1();
  }

}
