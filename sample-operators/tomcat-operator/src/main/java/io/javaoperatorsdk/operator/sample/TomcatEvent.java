package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.client.Watcher;
import io.javaoperatorsdk.operator.processing.event.DefaultEvent;

public class TomcatEvent extends DefaultEvent {

  private final Watcher.Action action;
  private final Tomcat tomcat;

  public TomcatEvent(
      Watcher.Action action, Tomcat resource, TomcatEventSource tomcatEventSource, String webappUid) {
    super(webappUid, tomcatEventSource);
    this.action = action;
    this.tomcat = resource;
  }

  public Watcher.Action getAction() {
    return action;
  }

  public String resourceUid() {
    return getTomcat().getMetadata().getUid();
  }

  @Override
  public String toString() {
    return "CustomResourceEvent{"
        + "action="
        + action
        + ", resource=[ name="
        + getTomcat().getMetadata().getName()
        + ", kind="
        + getTomcat().getKind()
        + ", apiVersion="
        + getTomcat().getApiVersion()
        + " ,resourceVersion="
        + getTomcat().getMetadata().getResourceVersion()
        + ", markedForDeletion: "
        + (getTomcat().getMetadata().getDeletionTimestamp() != null
            && !getTomcat().getMetadata().getDeletionTimestamp().isEmpty())
        + " ]"
        + '}';
  }

  public Tomcat getTomcat() {
    return tomcat;
  }
}
