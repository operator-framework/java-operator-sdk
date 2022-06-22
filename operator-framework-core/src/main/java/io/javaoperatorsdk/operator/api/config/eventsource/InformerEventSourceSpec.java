package io.javaoperatorsdk.operator.api.config.eventsource;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

public class InformerEventSourceSpec<R extends HasMetadata> extends EventSourceSpec<R> {

  private final String labelSelector;

  private final Set<String> namespaces;

  private final boolean followNamespaceChanges;

  private final SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;


  public InformerEventSourceSpec(String name, Class<R> resourceType, String labelSelector,
      Set<String> namespaces,
      boolean followNamespaceChanges, SecondaryToPrimaryMapper secondaryToPrimaryMapper) {
    super(name, resourceType);
    this.labelSelector = labelSelector;
    this.namespaces = namespaces;
    this.followNamespaceChanges = followNamespaceChanges;
    this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
  }

  public String getLabelSelector() {
    return labelSelector;
  }

  public Set<String> getNamespaces() {
    return namespaces;
  }

  public boolean isFollowNamespaceChanges() {
    return followNamespaceChanges;
  }

  public SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
    return secondaryToPrimaryMapper;
  }
}
