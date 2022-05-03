package io.javaoperatorsdk.operator.sample.changenamespace;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
public class ChangeNamespaceTestCustomResource
    extends CustomResource<Void, ChangeNamespaceTestCustomResourceStatus>
    implements Namespaced {

  @Override
  protected ChangeNamespaceTestCustomResourceStatus initStatus() {
    return new ChangeNamespaceTestCustomResourceStatus();
  }
}
