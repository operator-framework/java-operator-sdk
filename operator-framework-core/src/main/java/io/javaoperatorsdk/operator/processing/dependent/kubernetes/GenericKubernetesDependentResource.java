package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

public class GenericKubernetesDependentResource<P extends HasMetadata>
    extends KubernetesDependentResource<GenericKubernetesResource, P> {

  private GroupVersionKind groupVersionKind;

  public GenericKubernetesDependentResource(GroupVersionKind groupVersionKind) {
    super(GenericKubernetesResource.class);
    this.groupVersionKind = groupVersionKind;
  }

  // todo super functionality filters etc
  @Override
  protected InformerEventSource<GenericKubernetesResource, P> createEventSource(
      EventSourceContext<P> context) {
    var es = new InformerEventSource<>(
        InformerConfiguration.<GenericKubernetesResource>from(groupVersionKind, context)
            .build(),
        context);

    return es;
  }

  public GroupVersionKind getGroupVersionKind() {
    return groupVersionKind;
  }
}
