package io.javaoperatorsdk.operator.api.config.eventsource;

import java.util.Optional;
import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

@SuppressWarnings("rawtypes")
public class InformerEventSourceSpec extends EventSourceSpec {

  private final String labelSelector;

  private final Set<String> namespaces;

  private final boolean followNamespaceChanges;

  private final SecondaryToPrimaryMapper secondaryToPrimaryMapper;

  private PrimaryToSecondaryMapper primaryToSecondaryMapper;

  private Class resourceType;

  public InformerEventSourceSpec(String name, Class resourceType, String labelSelector,
      Set<String> namespaces,
      boolean followNamespaceChanges, SecondaryToPrimaryMapper secondaryToPrimaryMapper,
      PrimaryToSecondaryMapper primaryToSecondaryMapper) {
    super(name);
    this.labelSelector = labelSelector;
    this.namespaces = namespaces;
    this.followNamespaceChanges = followNamespaceChanges;
    this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
    this.primaryToSecondaryMapper = primaryToSecondaryMapper;
    this.resourceType = resourceType;
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

  public SecondaryToPrimaryMapper getSecondaryToPrimaryMapper() {
    return secondaryToPrimaryMapper;
  }

  public Optional<PrimaryToSecondaryMapper> getPrimaryToSecondaryMapper() {
    return Optional.ofNullable(primaryToSecondaryMapper);
  }

  public Class getResourceType() {
    return resourceType;
  }
}
