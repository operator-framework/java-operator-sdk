package io.javaoperatorsdk.operator.sample.createupdateeventfilter;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("cue")
public class CreateUpdateEventFilterTestCustomResource
    extends
    CustomResource<CreateUpdateEventFilterTestCustomResourceSpec, CreateUpdateEventFilterTestCustomResourceStatus>
    implements Namespaced {

  @Override
  protected CreateUpdateEventFilterTestCustomResourceStatus initStatus() {
    return new CreateUpdateEventFilterTestCustomResourceStatus();
  }

}
