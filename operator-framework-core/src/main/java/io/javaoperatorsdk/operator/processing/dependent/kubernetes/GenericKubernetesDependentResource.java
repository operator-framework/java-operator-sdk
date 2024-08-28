package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;

public class GenericKubernetesDependentResource<P extends HasMetadata>
    extends KubernetesDependentResource<GenericKubernetesResource, P> {

  private final GroupVersionKindPlural groupVersionKind;

  public GenericKubernetesDependentResource(GroupVersionKind groupVersionKind) {
    this(new GroupVersionKindPlural(groupVersionKind));
  }

  public GenericKubernetesDependentResource(GroupVersionKindPlural groupVersionKind) {
    super(GenericKubernetesResource.class);
    this.groupVersionKind = groupVersionKind;
  }

  protected InformerConfiguration.InformerConfigurationBuilder<GenericKubernetesResource> informerConfigurationBuilder() {
    return InformerConfiguration.from(groupVersionKind);
  }

  @SuppressWarnings("unused")
  public GroupVersionKindPlural getGroupVersionKind() {
    return groupVersionKind;
  }
}
