package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import io.javaoperatorsdk.operator.processing.KubernetesResourceUtils;
import io.javaoperatorsdk.operator.processing.event.DefaultEvent;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;

public class CustomResourceEvent extends DefaultEvent {

  private final Watcher.Action action;
  private final CustomResource customResource;

  public CustomResourceEvent(
      Watcher.Action action,
      CustomResource resource,
      CustomResourceEventSource customResourceEventSource) {
    super(KubernetesResourceUtils.getUID(resource), customResourceEventSource);
    this.action = action;
    this.customResource = resource;
  }

  public Watcher.Action getAction() {
    return action;
  }

  public String resourceUid() {
    return getCustomResource().getMetadata().getUid();
  }

  @Override
  public String toString() {
    return "CustomResourceEvent{"
        + "action="
        + action
        + ", resource=[ name="
        + getName(getCustomResource())
        + ", kind="
        + getCustomResource().getKind()
        + ", apiVersion="
        + getCustomResource().getApiVersion()
        + " ,resourceVersion="
        + getCustomResource().getMetadata().getResourceVersion()
        + ", markedForDeletion: "
        + (getCustomResource().getMetadata().getDeletionTimestamp() != null
            && !getCustomResource().getMetadata().getDeletionTimestamp().isEmpty())
        + " ]}";
  }

  public CustomResource getCustomResource() {
    return customResource;
  }
}
