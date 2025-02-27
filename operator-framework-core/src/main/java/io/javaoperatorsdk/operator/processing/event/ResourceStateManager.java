package io.javaoperatorsdk.operator.processing.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class ResourceStateManager {
  // maybe we should have a way for users to specify a hint on the amount of CRs their reconciler
  // will process to avoid under- or over-sizing the state maps and avoid too many resizing that
  // take time and memory?
  private final Map<ResourceID, ResourceState> states = new ConcurrentHashMap<>(100);

  public ResourceState getOrCreate(ResourceID resourceID) {
    return states.computeIfAbsent(resourceID, ResourceState::new);
  }

  public ResourceState remove(ResourceID resourceID) {
    return states.remove(resourceID);
  }

  public boolean contains(ResourceID resourceID) {
    return states.containsKey(resourceID);
  }

  public List<ResourceState> resourcesWithEventPresent() {
    return states.values().stream()
        .filter(state -> !state.noEventPresent())
        .collect(Collectors.toList());
  }
}
