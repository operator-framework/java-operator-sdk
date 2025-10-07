package io.javaoperatorsdk.operator.processing.event;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;

class ResourceStateManager {
  // maybe we should have a way for users to specify a hint on the amount of CRs their reconciler
  // will process to avoid under- or over-sizing the state maps and avoid too many resizing that
  // take time and memory?
  private final Map<ResourceID, ResourceState> states = new ConcurrentHashMap<>(100);

  public Optional<ResourceState> getOrCreateOnResourceEvent(Event event) {
    var resourceId = event.getRelatedCustomResourceID();
    var state = states.get(event.getRelatedCustomResourceID());
    if (state != null) {
      return Optional.of(state);
    }
    if (event instanceof ResourceEvent) {
      state = new ResourceState(resourceId);
      states.put(resourceId, state);
      return Optional.of(state);
    } else {
      return Optional.empty();
    }
  }

  public ResourceState getOrCreate(ResourceID resourceID) {
    return states.computeIfAbsent(resourceID, ResourceState::new);
  }

  public Optional<ResourceState> get(ResourceID resourceID) {
    return Optional.ofNullable(states.get(resourceID));
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
