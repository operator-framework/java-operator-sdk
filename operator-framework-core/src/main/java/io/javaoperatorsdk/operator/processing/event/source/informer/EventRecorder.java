package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class EventRecorder<R extends HasMetadata> {

  private final Map<ResourceID, ArrayList<R>> resourceEvents = new HashMap<>();

  public void startEventRecording(ResourceID resourceID) {
    resourceEvents.putIfAbsent(resourceID, new ArrayList<>(5));
  }

  public boolean isRecordingFor(ResourceID resourceID) {
    return resourceEvents.get(resourceID) != null;
  }

  public void stopEventRecording(ResourceID resourceID) {
    resourceEvents.remove(resourceID);
  }

  public void recordEvent(R resource) {
    resourceEvents.get(ResourceID.fromResource(resource)).add(resource);
  }

  public boolean containsEventWithResourceVersion(ResourceID resourceID,
      String resourceVersion) {
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
    if (resources == null) {
      throw new IllegalStateException(
          "Null events list, this is probably a result of invalid usage of the " +
              "InformerEventSource. Resource ID: " + resourceID);
    }
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
    if (resources == null) {
      throw new IllegalStateException(
          "Null events list, this is probably a result of invalid usage of the " +
              "InformerEventSource. Resource ID: " + resourceID);
    }
    return resources.get(resources.size() - 1);
  }
}
