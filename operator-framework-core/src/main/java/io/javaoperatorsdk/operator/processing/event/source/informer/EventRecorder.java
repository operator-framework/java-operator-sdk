package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ObjectKey;

public class EventRecorder<R extends HasMetadata> {

  private final Map<ObjectKey, ArrayList<R>> resourceEvents = new HashMap<>();

  public void startEventRecording(ObjectKey objectKey) {
    resourceEvents.putIfAbsent(objectKey, new ArrayList<>(5));
  }

  public boolean isRecordingFor(ObjectKey objectKey) {
    return resourceEvents.get(objectKey) != null;
  }

  public void stopEventRecording(ObjectKey objectKey) {
    resourceEvents.remove(objectKey);
  }

  public void recordEvent(R resource) {
    resourceEvents.get(ObjectKey.fromResource(resource)).add(resource);
  }

  public boolean containsEventWithResourceVersion(ObjectKey objectKey,
      String resourceVersion) {
    List<R> events = resourceEvents.get(objectKey);
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
      ObjectKey objectKey, String resourceVersion) {
    List<R> resources = resourceEvents.get(objectKey);
    if (resources == null) {
      throw new IllegalStateException(
          "Null events list, this is probably a result of invalid usage of the " +
              "InformerEventSource. Resource ID: " + objectKey);
    }
    if (resources.isEmpty()) {
      throw new IllegalStateException("No events for resource id: " + objectKey);
    }
    return !resources
        .get(resources.size() - 1)
        .getMetadata()
        .getResourceVersion()
        .equals(resourceVersion);
  }

  public R getLastEvent(ObjectKey objectKey) {
    List<R> resources = resourceEvents.get(objectKey);
    if (resources == null) {
      throw new IllegalStateException(
          "Null events list, this is probably a result of invalid usage of the " +
              "InformerEventSource. Resource ID: " + objectKey);
    }
    return resources.get(resources.size() - 1);
  }
}
