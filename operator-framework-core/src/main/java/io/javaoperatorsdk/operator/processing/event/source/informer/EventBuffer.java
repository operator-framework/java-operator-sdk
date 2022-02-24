package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class EventBuffer<R extends HasMetadata> {

  private final Map<ResourceID, ArrayList<R>> resourceEvents = new ConcurrentHashMap<>();

  void startEventRecording(ResourceID resourceID) {
    resourceEvents.putIfAbsent(resourceID, new ArrayList<>());
  }

  public boolean isEventsRecordedFor(ResourceID resourceID) {
    return resourceEvents.get(resourceID) != null;
  }

  public void stopEventRecording(ResourceID resourceID) {
    resourceEvents.remove(resourceID);
  }

  public void eventReceived(R resource) {
    resourceEvents.get(ResourceID.fromResource(resource)).add(resource);
  }

  public boolean containsEventsFor(ResourceID resourceID) {
    return !resourceEvents.get(resourceID).isEmpty();
  }

  public boolean containsEventWithResourceVersion(ResourceID resourceID, String resourceVersion) {
    List<R> events = resourceEvents.get(resourceID);
    if (events == null) {
      return false;
    }
    if (events.isEmpty()) {
      return false;
    } else {
      return events.stream()
          .anyMatch(e -> e.getMetadata().getResourceVersion().equals(resourceVersion));
    }
  }

  public boolean containsEventWithVersionButItsNotLastOne(
      ResourceID resourceID, String resourceVersion) {
    List<R> resources = resourceEvents.get(resourceID);
    if (resources.isEmpty()) {
      throw new IllegalStateException("No events for resource id: " + resourceID);
    }
    return !resources
        .get(resources.size() - 1)
        .getMetadata()
        .getResourceVersion()
        .equals(resourceVersion);
  }

  public R getLastEvent(ResourceID resourceID) {
    List<R> resources = resourceEvents.get(resourceID);
    return resources.get(resources.size() - 1);
  }
}
