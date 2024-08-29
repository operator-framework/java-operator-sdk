package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;

public class GenericKubernetesDependentResource<P extends HasMetadata>
    extends KubernetesDependentResource<GenericKubernetesResource, P> {

  private final GroupVersionKindPlural groupVersionKind;

  public GenericKubernetesDependentResource(GroupVersionKind groupVersionKind) {
    this(GroupVersionKindPlural.from(groupVersionKind));
  }

  public GenericKubernetesDependentResource(GroupVersionKindPlural groupVersionKind) {
    super(GenericKubernetesResource.class);
    this.groupVersionKind = groupVersionKind;
  }

  protected InformerEventSourceConfiguration.Builder<GenericKubernetesResource> informerConfigurationBuilder(
      EventSourceContext<P> context) {
    return InformerEventSourceConfiguration.from(groupVersionKind,
        context.getPrimaryResourceClass());
  }

  @SuppressWarnings("unused")
  public GroupVersionKindPlural getGroupVersionKind() {
    return groupVersionKind;
  }
}
