package io.javaoperatorsdk.operator.sample.conditionchecker;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("cch")
public class ConditionCheckerTestCustomResource
    extends
    CustomResource<ConditionCheckerTestCustomResourceSpec, ConditionCheckerTestCustomResourceStatus>
    implements Namespaced {


  @Override
  protected ConditionCheckerTestCustomResourceStatus initStatus() {
    return new ConditionCheckerTestCustomResourceStatus();
  }
}
